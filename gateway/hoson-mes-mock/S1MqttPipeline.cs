using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Web.Script.Serialization;

internal sealed class S1PublishMessage
{
    public string Id { get; set; }
    public string SourceDeviceCode { get; set; }
    public string Topic { get; set; }
    public string Payload { get; set; }
    public int Attempts { get; set; }
    public DateTime NextAttemptUtc { get; set; }
}

internal static class S1EventMapper
{
    private const int PropertyValueLimit = 64;
    private const int EventRemarkLimit = 200;

    internal static List<S1PublishMessage> Map(MesExchange exchange, S1DeviceMapping mapping)
    {
        if (exchange == null) throw new ArgumentNullException("exchange");
        if (mapping == null || string.IsNullOrWhiteSpace(mapping.SourceDeviceCode)) throw new ArgumentException("S1 device mapping is required.");
        Dictionary<string, object> envelope = DeserializeObject(exchange.RequestBody);
        string method = Text(envelope, "method");
        if (string.IsNullOrWhiteSpace(method)) method = exchange.Method;
        method = (method ?? "").Trim().ToLowerInvariant();
        Dictionary<string, object> data = Dictionary(envelope, "data") ?? new Dictionary<string, object>();
        DateTime timestamp = exchange.Time == default(DateTime) ? DateTime.UtcNow : exchange.Time.ToUniversalTime();
        long unixMilliseconds = UnixMilliseconds(timestamp);
        List<Dictionary<string, object>> properties = new List<Dictionary<string, object>>();
        List<Dictionary<string, object>> events = new List<Dictionary<string, object>>();

        if (method == "login")
        {
            AddProperty(properties, "host_ip", First(data, "host_ip", exchange.ClientIp), unixMilliseconds, "device login address");
            AddEvent(events, "device_login", "success", unixMilliseconds, "device login");
        }
        else if (method == "device_status")
        {
            string status = First(data, "status");
            string alarmCode = First(Dictionary(data, "error_info"), "error_code", First(data, "error_code"));
            string alarmMessage = First(data, "message", First(Dictionary(data, "error_info"), "message"));
            AddProperty(properties, "run_status", MapRunStatus(status), unixMilliseconds, "device status");
            AddProperty(properties, "alarm_code", alarmCode, unixMilliseconds, "device alarm code");
            AddProperty(properties, "alarm_message", alarmMessage, unixMilliseconds, "device alarm message");
            AddEvent(events, "status_changed", MapRunStatus(status), unixMilliseconds, Summary("status " + status, alarmCode, alarmMessage));
        }
        else if (method == "feed_material")
        {
            string workOrder = First(data, "lot_no", First(data, "work_order_no"));
            string feedStatus = First(data, "feed_status");
            AddProperty(properties, "work_order_no", workOrder, unixMilliseconds, "material work order");
            AddProperty(properties, "feed_status", feedStatus, unixMilliseconds, "material feed status");
            AddEvent(events, "material_feed", feedStatus, unixMilliseconds, Summary("work order " + workOrder, "feed " + feedStatus));
        }
        else if (method == "scan_result")
        {
            string result = First(data, "result", First(data, "scan_result"));
            string content = First(data, "scan_content", First(data, "content"));
            AddProperty(properties, "last_scan_result", result, unixMilliseconds, "scan result");
            AddProperty(properties, "last_scan_content", content, unixMilliseconds, "scan content");
            AddEvent(events, "scan_result", result, unixMilliseconds, content);
        }
        else if (method == "return_labels_result")
        {
            string workOrder = First(data, "lot_no", First(data, "work_order_no"));
            string printResult = First(data, "print_result");
            string alarmCode = First(data, "error_code");
            string alarmMessage = First(data, "error_message", First(data, "message"));
            AddProperty(properties, "work_order_no", workOrder, unixMilliseconds, "print work order");
            AddProperty(properties, "last_print_result", printResult, unixMilliseconds, "print result");
            AddProperty(properties, "alarm_code", alarmCode, unixMilliseconds, "print error code");
            AddProperty(properties, "alarm_message", alarmMessage, unixMilliseconds, "print error message");
            AddEvent(events, "print_result", printResult, unixMilliseconds, Summary("work order " + workOrder, alarmCode, alarmMessage));
        }
        else if (method == "carft_param" || method == "craft_param")
        {
            AddEvent(events, "recipe_reported", "reported", unixMilliseconds, "recipe reported");
        }
        else if (method == "version_info")
        {
            string version = First(data, "software_version", First(data, "version", First(envelope, "version")));
            AddProperty(properties, "software_version", version, unixMilliseconds, "software version");
            AddEvent(events, "version_reported", version, unixMilliseconds, "software version " + version);
        }
        else return new List<S1PublishMessage>();

        List<S1PublishMessage> messages = new List<S1PublishMessage>();
        string baseId = MessageBaseId(exchange, mapping, envelope, method);
        if (properties.Count > 0) messages.Add(CreateMessage(baseId + "-property", mapping, "property/post", properties));
        if (events.Count > 0) messages.Add(CreateMessage(baseId + "-event", mapping, "event/post", events));
        return messages;
    }

    private static S1PublishMessage CreateMessage(string id, S1DeviceMapping mapping, string suffix, List<Dictionary<string, object>> values)
    {
        S1PublishMessage message = new S1PublishMessage();
        message.Id = id;
        message.SourceDeviceCode = mapping.SourceDeviceCode.Trim();
        message.Topic = "/" + mapping.ProductId.ToString(CultureInfo.InvariantCulture) + "/" + mapping.SerialNumber.Trim() + "/" + suffix;
        message.Payload = new JavaScriptSerializer().Serialize(values);
        message.Attempts = 0;
        message.NextAttemptUtc = DateTime.MinValue;
        return message;
    }

    private static void AddProperty(List<Dictionary<string, object>> values, string id, string value, long timestamp, string remark)
    {
        if (string.IsNullOrEmpty(value)) return;
        Add(values, id, Limit(value, PropertyValueLimit), timestamp, Limit(remark, EventRemarkLimit));
    }

    private static void AddEvent(List<Dictionary<string, object>> values, string id, string value, long timestamp, string remark)
    {
        Add(values, id, Limit(value, PropertyValueLimit), timestamp, Limit(remark, EventRemarkLimit));
    }

    private static void Add(List<Dictionary<string, object>> values, string id, string value, long timestamp, string remark)
    {
        Dictionary<string, object> item = new Dictionary<string, object>();
        item["id"] = id;
        item["value"] = value ?? "";
        item["ts"] = timestamp;
        item["remark"] = remark ?? "";
        values.Add(item);
    }

    private static string MessageBaseId(MesExchange exchange, S1DeviceMapping mapping, Dictionary<string, object> envelope, string method)
    {
        string requestId = Text(envelope, "id");
        string source = (mapping.SourceDeviceCode ?? "").Trim().ToUpperInvariant();
        if (!string.IsNullOrWhiteSpace(requestId)) return source + "-" + method + "-" + requestId;
        string value = source + "\n" + method + "\n" + (exchange.RequestBody ?? "") + "\n" + exchange.Time.ToUniversalTime().Ticks.ToString(CultureInfo.InvariantCulture);
        using (SHA256 hash = SHA256.Create()) return source + "-" + method + "-" + Convert.ToBase64String(hash.ComputeHash(Encoding.UTF8.GetBytes(value))).TrimEnd('=').Replace('+', '-').Replace('/', '_');
    }

    private static string MapRunStatus(string status)
    {
        if (status == "1" || status == "2" || status == "3") return status;
        return "0";
    }

    private static string Summary(params string[] parts)
    {
        StringBuilder result = new StringBuilder();
        foreach (string part in parts)
        {
            if (string.IsNullOrWhiteSpace(part)) continue;
            if (result.Length > 0) result.Append("; ");
            result.Append(part);
        }
        return Limit(result.ToString(), EventRemarkLimit);
    }

    private static Dictionary<string, object> DeserializeObject(string json)
    {
        try
        {
            Dictionary<string, object> value = new JavaScriptSerializer().DeserializeObject(json ?? "") as Dictionary<string, object>;
            if (value == null) throw new ArgumentException("Hoson request must be a JSON object.");
            return value;
        }
        catch (InvalidOperationException error) { throw new ArgumentException("Hoson request must be valid JSON.", error); }
        catch (ArgumentException error) { throw new ArgumentException("Hoson request must be valid JSON.", error); }
    }

    private static Dictionary<string, object> Dictionary(Dictionary<string, object> source, string key)
    {
        if (source == null) return null;
        object value;
        return source.TryGetValue(key, out value) ? value as Dictionary<string, object> : null;
    }

    private static string First(Dictionary<string, object> source, params string[] keys)
    {
        if (source == null) return "";
        foreach (string key in keys)
        {
            object value;
            if (source.TryGetValue(key, out value) && value != null) return Convert.ToString(value, CultureInfo.InvariantCulture);
        }
        return "";
    }

    private static string Text(Dictionary<string, object> source, string key) { return First(source, key); }
    private static string Limit(string value, int maximum) { return value != null && value.Length > maximum ? value.Substring(0, maximum) : (value ?? ""); }
    private static long UnixMilliseconds(DateTime value) { return (value.ToUniversalTime().Ticks - 621355968000000000L) / TimeSpan.TicksPerMillisecond; }
}

internal sealed class S1Outbox
{
    private sealed class State
    {
        public List<S1PublishMessage> Pending { get; set; }
        public Dictionary<string, DateTime> Completed { get; set; }

        public State()
        {
            Pending = new List<S1PublishMessage>();
            Completed = new Dictionary<string, DateTime>(StringComparer.Ordinal);
        }
    }

    private const int CompletedLimit = 10000;
    private readonly string path;
    private readonly object sync = new object();
    private State state;

    internal S1Outbox(string filePath)
    {
        if (string.IsNullOrWhiteSpace(filePath)) throw new ArgumentException("S1 outbox path is required.");
        path = filePath;
        state = Load();
    }

    internal bool TryEnqueue(IEnumerable<S1PublishMessage> messages, out string error)
    {
        error = null;
        if (messages == null) return true;
        lock (sync)
        {
            State before = Clone(state);
            try
            {
                TrimCompleted(DateTime.UtcNow);
                foreach (S1PublishMessage message in messages)
                {
                    if (message == null || string.IsNullOrWhiteSpace(message.Id)) throw new ArgumentException("S1 publish message ID is required.");
                    if (state.Completed.ContainsKey(message.Id) || ContainsPending(message.Id)) continue;
                    state.Pending.Add(Clone(message));
                }
                Persist();
                return true;
            }
            catch (Exception exception)
            {
                state = before;
                error = exception.Message;
                return false;
            }
        }
    }

    internal List<S1PublishMessage> GetDue(DateTime utcNow)
    {
        lock (sync)
        {
            List<S1PublishMessage> due = new List<S1PublishMessage>();
            foreach (S1PublishMessage message in state.Pending)
                if (message.NextAttemptUtc <= utcNow) due.Add(Clone(message));
            return due;
        }
    }

    internal void Acknowledge(string id)
    {
        if (string.IsNullOrWhiteSpace(id)) return;
        lock (sync)
        {
            State before = Clone(state);
            try
            {
                state.Pending.RemoveAll(delegate(S1PublishMessage message) { return string.Equals(message.Id, id, StringComparison.Ordinal); });
                state.Completed[id] = DateTime.UtcNow;
                TrimCompleted(DateTime.UtcNow);
                Persist();
            }
            catch
            {
                state = before;
                throw;
            }
        }
    }

    internal void Reschedule(string id, DateTime nextAttemptUtc)
    {
        if (string.IsNullOrWhiteSpace(id)) return;
        lock (sync)
        {
            State before = Clone(state);
            try
            {
                foreach (S1PublishMessage message in state.Pending)
                    if (string.Equals(message.Id, id, StringComparison.Ordinal))
                    {
                        message.Attempts++;
                        message.NextAttemptUtc = nextAttemptUtc.ToUniversalTime();
                        break;
                    }
                Persist();
            }
            catch
            {
                state = before;
                throw;
            }
        }
    }

    private State Load()
    {
        if (!File.Exists(path)) return new State();
        State loaded = new JavaScriptSerializer().Deserialize<State>(File.ReadAllText(path, Encoding.UTF8));
        if (loaded == null) throw new InvalidDataException("S1 outbox must be a JSON object.");
        if (loaded.Pending == null) loaded.Pending = new List<S1PublishMessage>();
        if (loaded.Completed == null) loaded.Completed = new Dictionary<string, DateTime>(StringComparer.Ordinal);
        TrimCompleted(loaded, DateTime.UtcNow);
        return loaded;
    }

    private void Persist()
    {
        string directory = Path.GetDirectoryName(Path.GetFullPath(path));
        if (!Directory.Exists(directory)) Directory.CreateDirectory(directory);
        string temporary = path + ".tmp";
        try
        {
            File.WriteAllText(temporary, new JavaScriptSerializer().Serialize(state), new UTF8Encoding(false));
            if (File.Exists(path))
            {
                try { File.Replace(temporary, path, null); }
                catch (PlatformNotSupportedException) { File.Delete(path); File.Move(temporary, path); }
                catch (IOException) { File.Delete(path); File.Move(temporary, path); }
            }
            else File.Move(temporary, path);
        }
        finally
        {
            if (File.Exists(temporary)) File.Delete(temporary);
        }
    }

    private bool ContainsPending(string id)
    {
        foreach (S1PublishMessage message in state.Pending)
            if (string.Equals(message.Id, id, StringComparison.Ordinal)) return true;
        return false;
    }

    private void TrimCompleted(DateTime utcNow) { TrimCompleted(state, utcNow); }

    private static void TrimCompleted(State target, DateTime utcNow)
    {
        List<string> expired = new List<string>();
        foreach (KeyValuePair<string, DateTime> entry in target.Completed)
            if (utcNow - entry.Value.ToUniversalTime() >= TimeSpan.FromHours(24)) expired.Add(entry.Key);
        foreach (string id in expired) target.Completed.Remove(id);
        if (target.Completed.Count <= CompletedLimit) return;
        List<KeyValuePair<string, DateTime>> ordered = new List<KeyValuePair<string, DateTime>>(target.Completed);
        ordered.Sort(delegate(KeyValuePair<string, DateTime> left, KeyValuePair<string, DateTime> right) { return left.Value.CompareTo(right.Value); });
        for (int i = 0; i < ordered.Count - CompletedLimit; i++) target.Completed.Remove(ordered[i].Key);
    }

    private static State Clone(State original)
    {
        State copy = new State();
        foreach (S1PublishMessage message in original.Pending) copy.Pending.Add(Clone(message));
        foreach (KeyValuePair<string, DateTime> entry in original.Completed) copy.Completed.Add(entry.Key, entry.Value);
        return copy;
    }

    private static S1PublishMessage Clone(S1PublishMessage source)
    {
        S1PublishMessage copy = new S1PublishMessage();
        copy.Id = source.Id;
        copy.SourceDeviceCode = source.SourceDeviceCode;
        copy.Topic = source.Topic;
        copy.Payload = source.Payload;
        copy.Attempts = source.Attempts;
        copy.NextAttemptUtc = source.NextAttemptUtc;
        return copy;
    }
}
