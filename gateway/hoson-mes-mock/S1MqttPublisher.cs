using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Threading;
using uPLibrary.Networking.M2Mqtt;
using uPLibrary.Networking.M2Mqtt.Messages;

internal interface IS1MqttSession
{
    void Connect(S1MqttSettings settings, S1DeviceMapping mapping, string password);
    ushort Publish(string topic, byte[] payload);
    void Disconnect();
    event Action<ushort, bool> Published;
    event Action<Exception> ConnectionClosed;
}

internal sealed class M2MqttSession : IS1MqttSession
{
    private MqttClient client;

    public event Action<ushort, bool> Published;
    public event Action<Exception> ConnectionClosed;

    public void Connect(S1MqttSettings settings, S1DeviceMapping mapping, string password)
    {
        Disconnect();
        client = new MqttClient(settings.Host, settings.Port, settings.UseTls, null);
        client.MqttMsgPublished += delegate(object sender, MqttMsgPublishedEventArgs args)
        {
            Action<ushort, bool> handler = Published;
            if (handler != null) handler(args.MessageId, true);
        };
        client.ConnectionClosed += delegate(object sender, EventArgs args)
        {
            Action<Exception> handler = ConnectionClosed;
            if (handler != null) handler(new IOException("S1 MQTT connection closed."));
        };
        byte returnCode = client.Connect(mapping.ClientId, mapping.UserName, password, false, 60);
        if (returnCode != 0)
        {
            Disconnect();
            throw new IOException("S1 MQTT broker rejected the device connection.");
        }
    }

    public ushort Publish(string topic, byte[] payload)
    {
        if (client == null || !client.IsConnected) throw new IOException("S1 MQTT session is not connected.");
        return client.Publish(topic, payload, MqttMsgBase.QOS_LEVEL_AT_LEAST_ONCE, false);
    }

    public void Disconnect()
    {
        MqttClient active = client;
        client = null;
        if (active == null) return;
        try { if (active.IsConnected) active.Disconnect(); }
        catch (Exception) { }
    }
}

internal sealed class S1MqttPublisher
{
    private sealed class InFlight
    {
        internal S1PublishMessage Message;
        internal DateTime SentUtc;
    }

    private sealed class SessionState
    {
        internal IS1MqttSession Session;
        internal bool Connected;
        internal readonly Dictionary<ushort, InFlight> InFlightByMessageId = new Dictionary<ushort, InFlight>();
        internal readonly HashSet<string> InFlightOutboxIds = new HashSet<string>(StringComparer.Ordinal);
    }

    private readonly S1MqttSettings settings;
    private readonly S1Outbox outbox;
    private readonly Func<S1DeviceMapping, IS1MqttSession> sessionFactory;
    private readonly Dictionary<string, SessionState> sessions = new Dictionary<string, SessionState>(StringComparer.OrdinalIgnoreCase);
    private readonly object sync = new object();
    private readonly AutoResetEvent wakeSignal = new AutoResetEvent(false);
    private Thread worker;
    private bool stopping;
    private DateTime? lastSuccessUtc;
    private string lastError;

    internal S1MqttPublisher(S1MqttSettings configuredSettings, S1Outbox configuredOutbox)
        : this(configuredSettings, configuredOutbox, delegate(S1DeviceMapping mapping) { return new M2MqttSession(); }) { }

    internal S1MqttPublisher(S1MqttSettings configuredSettings, S1Outbox configuredOutbox, Func<S1DeviceMapping, IS1MqttSession> configuredFactory)
    {
        if (configuredSettings == null) throw new ArgumentNullException("configuredSettings");
        if (configuredOutbox == null) throw new ArgumentNullException("configuredOutbox");
        if (configuredFactory == null) throw new ArgumentNullException("configuredFactory");
        settings = configuredSettings;
        outbox = configuredOutbox;
        sessionFactory = configuredFactory;
    }

    internal int ConnectedMappings
    {
        get
        {
            lock (sync)
            {
                int result = 0;
                foreach (SessionState state in sessions.Values) if (state.Connected) result++;
                return result;
            }
        }
    }

    internal int TotalMappings { get { return settings.Devices == null ? 0 : settings.Devices.Count; } }
    internal int PendingCount { get { return outbox.GetDue(DateTime.MaxValue).Count; } }
    internal DateTime? LastSuccessUtc { get { lock (sync) return lastSuccessUtc; } }
    internal string LastError { get { lock (sync) return lastError; } }

    internal void Start()
    {
        lock (sync)
        {
            if (worker != null) return;
            stopping = false;
            worker = new Thread(WorkerLoop);
            worker.IsBackground = true;
            worker.Start();
        }
    }

    internal void Stop(TimeSpan timeout)
    {
        Thread active;
        lock (sync)
        {
            stopping = true;
            active = worker;
        }
        wakeSignal.Set();
        if (active != null && Thread.CurrentThread != active) active.Join(timeout);
        lock (sync)
        {
            worker = null;
            foreach (SessionState state in sessions.Values)
            {
                state.Connected = false;
                try { state.Session.Disconnect(); }
                catch (Exception) { }
            }
            sessions.Clear();
        }
    }

    internal void Wake() { wakeSignal.Set(); }

    internal void RunOnce(DateTime utcNow)
    {
        if (!settings.Enabled) return;
        List<S1PublishMessage> messages = outbox.GetDue(utcNow);
        foreach (S1PublishMessage message in messages) Publish(message, utcNow);
    }

    private void WorkerLoop()
    {
        while (true)
        {
            lock (sync) if (stopping) return;
            RunOnce(DateTime.UtcNow);
            wakeSignal.WaitOne(500);
        }
    }

    private void Publish(S1PublishMessage message, DateTime sentUtc)
    {
        S1DeviceMapping mapping = MappingFor(message.SourceDeviceCode);
        if (mapping == null)
        {
            SetError("No S1 MQTT mapping exists for source device " + (message.SourceDeviceCode ?? "") + ".");
            return;
        }
        SessionState state;
        try
        {
            state = SessionFor(mapping);
            lock (sync)
            {
                if (state.InFlightOutboxIds.Contains(message.Id)) return;
            }
            ushort brokerMessageId = state.Session.Publish(message.Topic, Encoding.UTF8.GetBytes(message.Payload ?? ""));
            lock (sync)
            {
                InFlight flight = new InFlight();
                flight.Message = message;
                flight.SentUtc = sentUtc;
                state.InFlightByMessageId[brokerMessageId] = flight;
                state.InFlightOutboxIds.Add(message.Id);
            }
        }
        catch (Exception error)
        {
            ScheduleRetry(message, sentUtc, error);
        }
    }

    private SessionState SessionFor(S1DeviceMapping mapping)
    {
        SessionState state;
        lock (sync)
        {
            if (!sessions.TryGetValue(mapping.SourceDeviceCode, out state))
            {
                state = new SessionState();
                state.Session = sessionFactory(mapping);
                if (state.Session == null) throw new InvalidOperationException("S1 MQTT session factory returned null.");
                state.Session.Published += delegate(ushort messageId, bool success) { OnPublished(state, messageId, success); };
                state.Session.ConnectionClosed += delegate(Exception error) { OnConnectionClosed(state, error); };
                sessions.Add(mapping.SourceDeviceCode, state);
            }
        }
        if (!state.Connected)
        {
            string password = S1MqttSettingsStore.UnprotectPassword(mapping.ProtectedPassword);
            state.Session.Connect(settings, mapping, password);
            lock (sync) state.Connected = true;
        }
        return state;
    }

    private void OnPublished(SessionState state, ushort brokerMessageId, bool success)
    {
        InFlight flight;
        lock (sync)
        {
            if (!state.InFlightByMessageId.TryGetValue(brokerMessageId, out flight)) return;
            state.InFlightByMessageId.Remove(brokerMessageId);
            state.InFlightOutboxIds.Remove(flight.Message.Id);
        }
        try
        {
            if (success)
            {
                outbox.Acknowledge(flight.Message.Id);
                lock (sync)
                {
                    lastSuccessUtc = DateTime.UtcNow;
                    lastError = null;
                }
            }
            else ScheduleRetry(flight.Message, flight.SentUtc, new IOException("S1 MQTT publish was not acknowledged."));
        }
        catch (Exception error) { SetError(error.Message); }
    }

    private void OnConnectionClosed(SessionState state, Exception error)
    {
        List<InFlight> pending = new List<InFlight>();
        lock (sync)
        {
            state.Connected = false;
            foreach (InFlight flight in state.InFlightByMessageId.Values) pending.Add(flight);
            state.InFlightByMessageId.Clear();
            state.InFlightOutboxIds.Clear();
        }
        foreach (InFlight flight in pending) ScheduleRetry(flight.Message, DateTime.UtcNow, error);
        SetError(error == null ? "S1 MQTT connection closed." : error.Message);
    }

    private void ScheduleRetry(S1PublishMessage message, DateTime utcNow, Exception error)
    {
        try
        {
            int seconds = message.Attempts >= 6 ? 60 : 1 << message.Attempts;
            outbox.Reschedule(message.Id, utcNow.AddSeconds(seconds));
        }
        catch (Exception persistenceError) { SetError(persistenceError.Message); return; }
        SetError(error == null ? "S1 MQTT publish failed." : error.Message);
    }

    private S1DeviceMapping MappingFor(string sourceDeviceCode)
    {
        if (settings.Devices == null) return null;
        foreach (S1DeviceMapping mapping in settings.Devices)
            if (mapping != null && string.Equals(mapping.SourceDeviceCode, sourceDeviceCode, StringComparison.OrdinalIgnoreCase)) return mapping;
        return null;
    }

    private void SetError(string message)
    {
        lock (sync) lastError = message;
    }
}
