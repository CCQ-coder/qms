using System;
using System.Collections.Generic;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Web.Script.Serialization;

internal sealed class S1MqttSettings
{
    public int Version { get; set; }
    public bool Enabled { get; set; }
    public string Host { get; set; }
    public int Port { get; set; }
    public bool UseTls { get; set; }
    public List<S1DeviceMapping> Devices { get; set; }

    public S1MqttSettings()
    {
        Version = 1;
        Host = "127.0.0.1";
        Port = 1883;
        Devices = new List<S1DeviceMapping>();
    }
}

internal sealed class S1DeviceMapping
{
    public string SourceDeviceCode { get; set; }
    public long ProductId { get; set; }
    public string SerialNumber { get; set; }
    public string ClientId { get; set; }
    public string UserName { get; set; }
    public string ProtectedPassword { get; set; }
}

internal static class S1MqttSettingsStore
{
    internal static S1MqttSettings Load(string path)
    {
        if (string.IsNullOrWhiteSpace(path)) throw new ArgumentException("S1 MQTT configuration path is required.");
        if (!File.Exists(path)) return new S1MqttSettings();

        S1MqttSettings settings = new JavaScriptSerializer().Deserialize<S1MqttSettings>(File.ReadAllText(path, Encoding.UTF8));
        if (settings == null) throw new ArgumentException("S1 MQTT configuration must be a JSON object.");
        if (settings.Devices == null) settings.Devices = new List<S1DeviceMapping>();
        if (settings.Version == 0) settings.Version = 1;
        Validate(settings);
        return settings;
    }

    internal static void Save(string path, S1MqttSettings settings)
    {
        if (string.IsNullOrWhiteSpace(path)) throw new ArgumentException("S1 MQTT configuration path is required.");
        Validate(settings);

        string directory = Path.GetDirectoryName(Path.GetFullPath(path));
        if (!Directory.Exists(directory)) Directory.CreateDirectory(directory);
        string temporary = path + ".tmp";
        try
        {
            string json = new JavaScriptSerializer().Serialize(settings);
            File.WriteAllText(temporary, json, new UTF8Encoding(false));
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

    internal static string ProtectPassword(string clearText)
    {
        if (string.IsNullOrEmpty(clearText)) throw new ArgumentException("S1 MQTT password is required.");
        byte[] protectedBytes = ProtectedData.Protect(Encoding.UTF8.GetBytes(clearText), null, DataProtectionScope.CurrentUser);
        return Convert.ToBase64String(protectedBytes);
    }

    internal static string UnprotectPassword(string protectedValue)
    {
        if (string.IsNullOrWhiteSpace(protectedValue)) throw new ArgumentException("Protected S1 MQTT password is required.");
        byte[] clearBytes = ProtectedData.Unprotect(Convert.FromBase64String(protectedValue), null, DataProtectionScope.CurrentUser);
        return Encoding.UTF8.GetString(clearBytes);
    }

    internal static void Validate(S1MqttSettings settings)
    {
        if (settings == null) throw new ArgumentException("S1 MQTT configuration is required.");
        if (settings.Version != 1) throw new ArgumentException("Unsupported S1 MQTT configuration version.");
        if (settings.Port < 1 || settings.Port > 65535) throw new ArgumentException("S1 MQTT port must be between 1 and 65535.");
        if (settings.Devices == null) throw new ArgumentException("S1 MQTT device mappings are required.");
        if ((settings.Enabled || settings.Devices.Count > 0) && string.IsNullOrWhiteSpace(settings.Host))
            throw new ArgumentException("S1 MQTT broker host is required.");

        HashSet<string> sourceCodes = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        foreach (S1DeviceMapping device in settings.Devices)
        {
            if (device == null) throw new ArgumentException("S1 MQTT device mapping is required.");
            if (string.IsNullOrWhiteSpace(device.SourceDeviceCode) || !sourceCodes.Add(device.SourceDeviceCode.Trim()))
                throw new ArgumentException("Each S1 MQTT source device code must be unique.");
            if (device.ProductId <= 0) throw new ArgumentException("S1 MQTT product ID must be positive.");
            if (string.IsNullOrWhiteSpace(device.SerialNumber) || string.IsNullOrWhiteSpace(device.ClientId) || string.IsNullOrWhiteSpace(device.UserName))
                throw new ArgumentException("S1 MQTT serial number, client ID, and username are required.");
            if (string.IsNullOrWhiteSpace(device.ProtectedPassword)) throw new ArgumentException("S1 MQTT password is required.");
        }
    }
}
