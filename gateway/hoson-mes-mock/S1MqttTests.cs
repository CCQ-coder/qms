using System;
using System.Collections.Generic;
using System.IO;

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
