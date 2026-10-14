using System;
using System.Collections.Generic;
using System.IO;
using System.Web.Script.Serialization;

internal static class S1MqttTests
{
    private static int Main()
    {
        string directory = Path.Combine(Path.GetTempPath(), "hoson-s1-tests-" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(directory);
        try
        {
            DefaultConfigurationIsSafe(directory);
            SettingsRoundTripProtectsPasswords(directory);
            DuplicateDeviceCodesAreRejected(directory);
            InvalidPortIsRejected(directory);
            DeviceTokensAreUniqueAndResolveToTheirDevice();
            DeviceTokensExpireAfterTwentyFourHoursOfInactivity();
            ClearingSessionsSimulatesRestart();
            LoginMapsHostIpAndEvent();
            DeviceStatusMapsAllSupportedStates();
            FeedMaterialMapsWorkOrderAndStatus();
            ScanResultTruncatesPropertyAndEventSummary();
            PrintResultMapsStatusAndErrors();
            RecipeAliasesMapToRecipeEvent();
            VersionInfoMapsSoftwareVersion();
            OutboxReloadsRetriesAcknowledgesAndSuppressesDuplicates(directory);
            PublisherUsesDeviceCredentialsAndWaitsForPubAck(directory);
            PublisherReschedulesFailuresAndCapsBackoff(directory);
            PublisherReloadsUnacknowledgedMessages(directory);
            Console.WriteLine("S1 MQTT configuration and device-session checks passed.");
            return 0;
        }
        catch (Exception error)
        {
            Console.Error.WriteLine(error.GetType().FullName + ": " + error.Message);
            return 1;
        }
        finally
        {
            if (Directory.Exists(directory)) Directory.Delete(directory, true);
        }
    }

    private static void DefaultConfigurationIsSafe(string directory)
    {
        S1MqttSettings settings = S1MqttSettingsStore.Load(Path.Combine(directory, "absent.json"));
        Assert(!settings.Enabled, "missing configuration leaves forwarding disabled");
        Assert(settings.Port == 1883, "missing configuration uses MQTT default port");
        Assert(settings.Devices.Count == 0, "missing configuration has no device mappings");
    }

    private static void SettingsRoundTripProtectsPasswords(string directory)
    {
        string path = Path.Combine(directory, "s1_mqtt_config.json");
        S1MqttSettings settings = new S1MqttSettings();
        settings.Enabled = true;
        settings.Host = "mqtt.example.test";
        settings.Port = 2883;
        settings.UseTls = true;
        settings.Devices.Add(Device("DEVICE-001", 101, "S1-001", "client-001", "user-001", "clear-alpha-password"));
        settings.Devices.Add(Device("DEVICE-002", 102, "S1-002", "client-002", "user-002", "clear-beta-password"));

        S1MqttSettingsStore.Save(path, settings);
        string saved = File.ReadAllText(path);
        Assert(!saved.Contains("clear-alpha-password") && !saved.Contains("clear-beta-password"), "saved configuration excludes clear-text passwords");

        S1MqttSettings loaded = S1MqttSettingsStore.Load(path);
        Assert(loaded.Enabled && loaded.Host == "mqtt.example.test" && loaded.Port == 2883 && loaded.UseTls, "broker settings round trip");
        Assert(loaded.Devices.Count == 2, "device mappings round trip");
        Assert(loaded.Devices[0].SourceDeviceCode == "DEVICE-001" && loaded.Devices[1].SerialNumber == "S1-002", "device identity fields round trip");
        Assert(S1MqttSettingsStore.UnprotectPassword(loaded.Devices[0].ProtectedPassword) == "clear-alpha-password", "first DPAPI password round trips");
        Assert(S1MqttSettingsStore.UnprotectPassword(loaded.Devices[1].ProtectedPassword) == "clear-beta-password", "second DPAPI password round trips");
    }

    private static void DuplicateDeviceCodesAreRejected(string directory)
    {
        S1MqttSettings settings = new S1MqttSettings();
        settings.Host = "mqtt.example.test";
        settings.Devices.Add(Device("device-001", 1, "one", "client-one", "user-one", "one"));
        settings.Devices.Add(Device("DEVICE-001", 2, "two", "client-two", "user-two", "two"));
        AssertThrows(delegate { S1MqttSettingsStore.Save(Path.Combine(directory, "duplicate.json"), settings); }, "duplicate source device codes are rejected case-insensitively");
    }

    private static void InvalidPortIsRejected(string directory)
    {
        S1MqttSettings settings = new S1MqttSettings();
        settings.Host = "mqtt.example.test";
        settings.Port = 0;
        AssertThrows(delegate { S1MqttSettingsStore.Save(Path.Combine(directory, "invalid-port.json"), settings); }, "invalid MQTT port is rejected");
    }

    private static void DeviceTokensAreUniqueAndResolveToTheirDevice()
    {
        HosonDeviceSessionStore sessions = new HosonDeviceSessionStore();
        DateTime start = new DateTime(2026, 10, 14, 0, 0, 0, DateTimeKind.Utc);
        string first = sessions.Register("DEVICE-001", start);
        string second = sessions.Register("DEVICE-002", start);
        string deviceCode;
        Assert(!string.IsNullOrEmpty(first) && first.StartsWith("LYR-", StringComparison.Ordinal), "issued token has an opaque LYR prefix");
        Assert(first != second, "different logins receive distinct tokens");
        Assert(sessions.TryResolve(first, start.AddMinutes(1), out deviceCode) && deviceCode == "DEVICE-001", "token resolves to its registering device");
        Assert(sessions.TryResolve(second, start.AddMinutes(1), out deviceCode) && deviceCode == "DEVICE-002", "second token resolves to its registering device");
    }

    private static void DeviceTokensExpireAfterTwentyFourHoursOfInactivity()
    {
        HosonDeviceSessionStore sessions = new HosonDeviceSessionStore();
        DateTime start = new DateTime(2026, 10, 14, 0, 0, 0, DateTimeKind.Utc);
        string token = sessions.Register("DEVICE-001", start);
        string deviceCode;
        Assert(sessions.TryResolve(token, start.AddHours(23).AddMinutes(59), out deviceCode), "token is valid before the idle limit");
        Assert(sessions.TryResolve(token, start.AddHours(47).AddMinutes(58), out deviceCode), "successful lookup refreshes the idle lifetime");
        Assert(!sessions.TryResolve(token, start.AddHours(71).AddMinutes(59), out deviceCode), "token expires after twenty-four idle hours");
    }

    private static void ClearingSessionsSimulatesRestart()
    {
        HosonDeviceSessionStore sessions = new HosonDeviceSessionStore();
        DateTime now = new DateTime(2026, 10, 14, 0, 0, 0, DateTimeKind.Utc);
        string token = sessions.Register("DEVICE-001", now);
        string deviceCode;
        sessions.Clear();
        Assert(!sessions.TryResolve(token, now.AddMinutes(1), out deviceCode), "clearing in-memory sessions invalidates existing tokens");
    }

    private static void LoginMapsHostIpAndEvent()
    {
        List<S1PublishMessage> messages = S1EventMapper.Map(Exchange("login", "login-1", "{\"device_code\":\"DEVICE-001\",\"host_ip\":\"192.168.10.21\"}"), Mapping());
        Assert(messages.Count == 2, "login creates one property and one event publication");
        AssertValue(messages, PropertyTopic(), "host_ip", "192.168.10.21");
        AssertValue(messages, EventTopic(), "device_login", "success");
    }

    private static void DeviceStatusMapsAllSupportedStates()
    {
        string[] statuses = { "1", "2", "3" };
        foreach (string status in statuses)
        {
            List<S1PublishMessage> messages = S1EventMapper.Map(Exchange("device_status", "status-" + status,
                "{\"status\":\"" + status + "\",\"time\":\"2026-10-14 12:00:00\",\"message\":\"报警\",\"error_info\":{\"error_code\":\"E-01\",\"message\":\"故障详情\"}}"), Mapping());
            AssertValue(messages, PropertyTopic(), "run_status", status);
            AssertValue(messages, PropertyTopic(), "alarm_code", "E-01");
            AssertValue(messages, EventTopic(), "status_changed", status);
        }
    }

    private static void FeedMaterialMapsWorkOrderAndStatus()
    {
        List<S1PublishMessage> messages = S1EventMapper.Map(Exchange("feed_material", "feed-1", "{\"lot_no\":\"LOT-100\",\"feed_status\":\"loaded\"}"), Mapping());
        AssertValue(messages, PropertyTopic(), "work_order_no", "LOT-100");
        AssertValue(messages, PropertyTopic(), "feed_status", "loaded");
        AssertValue(messages, EventTopic(), "material_feed", "loaded");
    }

    private static void ScanResultTruncatesPropertyAndEventSummary()
    {
        string oversized = new string('X', 300);
        List<S1PublishMessage> messages = S1EventMapper.Map(Exchange("scan_result", "scan-1", "{\"result\":\"pass\",\"scan_content\":\"" + oversized + "\"}"), Mapping());
        AssertValue(messages, PropertyTopic(), "last_scan_result", "pass");
        Assert(PayloadValue(messages, PropertyTopic(), "last_scan_content").Length == 64, "property values are capped at 64 characters");
        Dictionary<string, object> item = PayloadItem(messages, EventTopic(), "scan_result");
        Assert(Convert.ToString(item["remark"]).Length == 200, "event remarks are capped at 200 characters");
    }

    private static void PrintResultMapsStatusAndErrors()
    {
        List<S1PublishMessage> messages = S1EventMapper.Map(Exchange("return_labels_result", "print-1", "{\"print_result\":\"1\",\"lot_no\":\"LOT-200\",\"error_code\":\"P-01\",\"error_message\":\"打印故障\"}"), Mapping());
        AssertValue(messages, PropertyTopic(), "work_order_no", "LOT-200");
        AssertValue(messages, PropertyTopic(), "last_print_result", "1");
        AssertValue(messages, PropertyTopic(), "alarm_code", "P-01");
        AssertValue(messages, EventTopic(), "print_result", "1");
    }

    private static void RecipeAliasesMapToRecipeEvent()
    {
        foreach (string method in new string[] { "carft_param", "craft_param" })
        {
            List<S1PublishMessage> messages = S1EventMapper.Map(Exchange(method, method + "-1", "{\"recipe\":\"R-1\"}"), Mapping());
            Assert(messages.Count == 1, "recipe reports do not create long-lived JSON properties");
            AssertValue(messages, EventTopic(), "recipe_reported", "reported");
        }
    }

    private static void VersionInfoMapsSoftwareVersion()
    {
        List<S1PublishMessage> messages = S1EventMapper.Map(Exchange("version_info", "version-1", "{\"software_version\":\"2.3.4\"}"), Mapping());
        AssertValue(messages, PropertyTopic(), "software_version", "2.3.4");
        AssertValue(messages, EventTopic(), "version_reported", "2.3.4");
    }

    private static void OutboxReloadsRetriesAcknowledgesAndSuppressesDuplicates(string directory)
    {
        string path = Path.Combine(directory, "data", "s1-pending-events.json");
        List<S1PublishMessage> mapped = S1EventMapper.Map(Exchange("login", "outbox-1", "{\"device_code\":\"DEVICE-001\",\"host_ip\":\"192.168.10.21\"}"), Mapping());
        string error;
        S1Outbox outbox = new S1Outbox(path);
        Assert(outbox.TryEnqueue(mapped, out error), "outbox persists mapped messages: " + error);
        Assert(File.Exists(path), "outbox write creates its data file");
        Assert(new S1Outbox(path).GetDue(DateTime.UtcNow).Count == 2, "outbox reload restores unacknowledged messages");

        DateTime retryAt = DateTime.UtcNow.AddMinutes(5);
        outbox.Reschedule(mapped[0].Id, retryAt);
        S1PublishMessage retried = FindById(outbox.GetDue(retryAt), mapped[0].Id);
        Assert(retried != null && retried.Attempts == 1 && retried.NextAttemptUtc == retryAt, "retry state is persisted on reschedule");
        outbox.Acknowledge(mapped[1].Id);
        Assert(FindById(outbox.GetDue(DateTime.UtcNow.AddHours(1)), mapped[1].Id) == null, "acknowledgement removes a published record");
        Assert(outbox.TryEnqueue(mapped, out error), "replayed request is accepted without write error");
        Assert(FindById(outbox.GetDue(DateTime.UtcNow.AddHours(1)), mapped[1].Id) == null, "completed request ID suppresses duplicate inbound publication");
    }

    private static S1DeviceMapping Mapping()
    {
        S1DeviceMapping mapping = new S1DeviceMapping();
        mapping.SourceDeviceCode = "DEVICE-001";
        mapping.ProductId = 42;
        mapping.SerialNumber = "S1-001";
        mapping.ClientId = "client";
        mapping.UserName = "user";
        mapping.ProtectedPassword = S1MqttSettingsStore.ProtectPassword("password");
        return mapping;
    }

    private static MesExchange Exchange(string method, string id, string data)
    {
        MesExchange exchange = new MesExchange();
        exchange.Time = new DateTime(2026, 10, 14, 12, 0, 0, DateTimeKind.Utc);
        exchange.Method = method;
        exchange.DeviceCode = "DEVICE-001";
        exchange.RequestBody = "{\"id\":\"" + id + "\",\"timestamp\":1791979200000,\"method\":\"" + method + "\",\"data\":" + data + "}";
        return exchange;
    }

    private static string PropertyTopic() { return "/42/S1-001/property/post"; }
    private static string EventTopic() { return "/42/S1-001/event/post"; }

    private static void AssertValue(List<S1PublishMessage> messages, string topic, string id, string expected)
    {
        Assert(PayloadValue(messages, topic, id) == expected, "payload item " + id + " uses expected value");
    }

    private static string PayloadValue(List<S1PublishMessage> messages, string topic, string id)
    {
        Dictionary<string, object> item = PayloadItem(messages, topic, id);
        return item == null ? null : Convert.ToString(item["value"]);
    }

    private static Dictionary<string, object> PayloadItem(List<S1PublishMessage> messages, string topic, string id)
    {
        S1PublishMessage message = MessageFor(messages, topic);
        Assert(message != null, "message for topic " + topic + " exists");
        object[] items = new JavaScriptSerializer().DeserializeObject(message.Payload) as object[];
        if (items != null)
            foreach (object candidate in items)
            {
                Dictionary<string, object> item = candidate as Dictionary<string, object>;
                if (item != null && Convert.ToString(item["id"]) == id) return item;
            }
        return null;
    }

    private static S1PublishMessage MessageFor(List<S1PublishMessage> messages, string topic)
    {
        foreach (S1PublishMessage message in messages)
            if (message.Topic == topic) return message;
        return null;
    }

    private static S1PublishMessage FindById(List<S1PublishMessage> messages, string id)
    {
        foreach (S1PublishMessage message in messages)
            if (message.Id == id) return message;
        return null;
    }

    private static void PublisherUsesDeviceCredentialsAndWaitsForPubAck(string directory)
    {
        S1DeviceMapping mapping = Mapping();
        S1Outbox outbox = QueueMessage(directory, "publisher-ack", mapping);
        FakeMqttSession fake = new FakeMqttSession();
        S1MqttPublisher publisher = new S1MqttPublisher(Settings(mapping), outbox, delegate(S1DeviceMapping ignored) { return fake; });
        DateTime now = DateTime.UtcNow;
        publisher.RunOnce(now);
        Assert(fake.ConnectedMapping == mapping && fake.ConnectedPassword == "password", "publisher connects with the mapping's own credentials");
        Assert(fake.PublishedTopics.Count == 1 && fake.PublishedTopics[0] == "/42/S1-001/property/post", "publisher sends only the stored S1 topic using the QoS 1 session contract");
        Assert(outbox.GetDue(now).Count == 1, "publisher retains a record before PUBACK");
        fake.RaisePublished(1, true);
        Assert(outbox.GetDue(now.AddHours(1)).Count == 0, "successful PUBACK removes the outbox record");
        publisher.Stop(TimeSpan.Zero);
    }

    private static void PublisherReschedulesFailuresAndCapsBackoff(string directory)
    {
        S1DeviceMapping mapping = Mapping();
        S1Outbox outbox = QueueMessage(directory, "publisher-retry", mapping);
        FakeMqttSession fake = new FakeMqttSession();
        S1MqttPublisher publisher = new S1MqttPublisher(Settings(mapping), outbox, delegate(S1DeviceMapping ignored) { return fake; });
        DateTime now = DateTime.UtcNow;
        publisher.RunOnce(now);
        fake.RaisePublished(1, false);
        S1PublishMessage retried = FindById(outbox.GetDue(now.AddSeconds(1)), "publisher-retry");
        Assert(retried != null && retried.Attempts == 1 && retried.NextAttemptUtc <= now.AddSeconds(1).AddMilliseconds(10), "failed PUBACK schedules first retry after one second");

        for (int i = 0; i < 5; i++) outbox.Reschedule("publisher-retry", now);
        publisher.RunOnce(now);
        fake.RaisePublished(2, false);
        retried = FindById(outbox.GetDue(now.AddMinutes(1)), "publisher-retry");
        Assert(retried != null && retried.Attempts == 7 && retried.NextAttemptUtc <= now.AddSeconds(60).AddMilliseconds(10), "retry delay is capped at sixty seconds");
        publisher.Stop(TimeSpan.Zero);
    }

    private static void PublisherReloadsUnacknowledgedMessages(string directory)
    {
        S1DeviceMapping mapping = Mapping();
        string path = Path.Combine(directory, "publisher-reload", "s1-pending-events.json");
        S1Outbox initial = new S1Outbox(path);
        string error;
        Assert(initial.TryEnqueue(new S1PublishMessage[] { Message("publisher-reload", mapping) }, out error), "publisher reload fixture queues its message");
        FakeMqttSession first = new FakeMqttSession();
        S1MqttPublisher publisher = new S1MqttPublisher(Settings(mapping), initial, delegate(S1DeviceMapping ignored) { return first; });
        publisher.RunOnce(DateTime.UtcNow);
        Assert(first.PublishedTopics.Count == 1, "first publisher emits the unacknowledged message");
        publisher.Stop(TimeSpan.Zero);

        S1Outbox restored = new S1Outbox(path);
        FakeMqttSession second = new FakeMqttSession();
        S1MqttPublisher restarted = new S1MqttPublisher(Settings(mapping), restored, delegate(S1DeviceMapping ignored) { return second; });
        restarted.RunOnce(DateTime.UtcNow);
        Assert(second.PublishedTopics.Count == 1, "restarted publisher re-emits the unacknowledged message");
        restarted.Stop(TimeSpan.Zero);
    }

    private static S1Outbox QueueMessage(string directory, string id, S1DeviceMapping mapping)
    {
        S1Outbox outbox = new S1Outbox(Path.Combine(directory, id, "s1-pending-events.json"));
        string error;
        Assert(outbox.TryEnqueue(new S1PublishMessage[] { Message(id, mapping) }, out error), "publisher fixture queues its message: " + error);
        return outbox;
    }

    private static S1PublishMessage Message(string id, S1DeviceMapping mapping)
    {
        S1PublishMessage message = new S1PublishMessage();
        message.Id = id;
        message.SourceDeviceCode = mapping.SourceDeviceCode;
        message.Topic = "/42/S1-001/property/post";
        message.Payload = "[]";
        message.NextAttemptUtc = DateTime.MinValue;
        return message;
    }

    private static S1MqttSettings Settings(S1DeviceMapping mapping)
    {
        S1MqttSettings settings = new S1MqttSettings();
        settings.Enabled = true;
        settings.Host = "mqtt.example.test";
        settings.Devices.Add(mapping);
        return settings;
    }

    private sealed class FakeMqttSession : IS1MqttSession
    {
        internal S1DeviceMapping ConnectedMapping;
        internal string ConnectedPassword;
        internal readonly List<string> PublishedTopics = new List<string>();
        public event Action<ushort, bool> Published;
        public event Action<Exception> ConnectionClosed;

        public void Connect(S1MqttSettings settings, S1DeviceMapping mapping, string password)
        {
            ConnectedMapping = mapping;
            ConnectedPassword = password;
        }

        public ushort Publish(string topic, byte[] payload)
        {
            PublishedTopics.Add(topic);
            return (ushort)PublishedTopics.Count;
        }

        public void Disconnect() { }

        internal void RaisePublished(ushort messageId, bool success)
        {
            Action<ushort, bool> handler = Published;
            if (handler != null) handler(messageId, success);
        }

        internal void RaiseConnectionClosed(Exception error)
        {
            Action<Exception> handler = ConnectionClosed;
            if (handler != null) handler(error);
        }
    }

    private static S1DeviceMapping Device(string deviceCode, long productId, string serialNumber, string clientId, string userName, string password)
    {
        S1DeviceMapping device = new S1DeviceMapping();
        device.SourceDeviceCode = deviceCode;
        device.ProductId = productId;
        device.SerialNumber = serialNumber;
        device.ClientId = clientId;
        device.UserName = userName;
        device.ProtectedPassword = S1MqttSettingsStore.ProtectPassword(password);
        return device;
    }

    private static void AssertThrows(Action action, string message)
    {
        try { action(); }
        catch (ArgumentException) { return; }
        throw new Exception("Failed: " + message);
    }

    private static void Assert(bool condition, string message)
    {
        if (!condition) throw new Exception("Failed: " + message);
    }
}
