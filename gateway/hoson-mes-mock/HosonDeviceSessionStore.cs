using System;
using System.Collections.Generic;
using System.Security.Cryptography;

internal sealed class HosonDeviceSessionStore
{
    private sealed class Session
    {
        internal string DeviceCode;
        internal DateTime ExpiresUtc;
    }

    private static readonly TimeSpan IdleLifetime = TimeSpan.FromHours(24);
    private readonly Dictionary<string, Session> sessions = new Dictionary<string, Session>(StringComparer.Ordinal);
    private readonly object sync = new object();

    internal string Register(string deviceCode, DateTime utcNow)
    {
        if (string.IsNullOrWhiteSpace(deviceCode)) throw new ArgumentException("Device code is required.");
        DateTime now = ToUtc(utcNow);
        byte[] bytes = new byte[32];
        string token;
        lock (sync)
        {
            do
            {
                using (RandomNumberGenerator random = RandomNumberGenerator.Create()) random.GetBytes(bytes);
                token = "LYR-" + Convert.ToBase64String(bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_');
            }
            while (sessions.ContainsKey(token));
            Session session = new Session();
            session.DeviceCode = deviceCode.Trim();
            session.ExpiresUtc = now.Add(IdleLifetime);
            sessions.Add(token, session);
        }
        return token;
    }

    internal bool TryResolve(string token, DateTime utcNow, out string deviceCode)
    {
        deviceCode = null;
        if (string.IsNullOrEmpty(token)) return false;
        DateTime now = ToUtc(utcNow);
        lock (sync)
        {
            Session session;
            if (!sessions.TryGetValue(token, out session)) return false;
            if (now >= session.ExpiresUtc)
            {
                sessions.Remove(token);
                return false;
            }
            session.ExpiresUtc = now.Add(IdleLifetime);
            deviceCode = session.DeviceCode;
            return true;
        }
    }

    internal void Clear()
    {
        lock (sync) sessions.Clear();
    }

    private static DateTime ToUtc(DateTime value)
    {
        return value.Kind == DateTimeKind.Utc ? value : value.ToUniversalTime();
    }
}
