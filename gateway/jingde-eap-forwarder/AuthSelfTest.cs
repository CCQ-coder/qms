using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;
using JingdeEapForwarder;

internal static class AuthSelfTest
{
    private static int logins;
    private static int uploads;
    private static Exception serverError;

    private static int Main()
    {
        var reserved = new TcpListener(IPAddress.Loopback, 0);
        reserved.Start();
        int port = ((IPEndPoint)reserved.LocalEndpoint).Port;
        reserved.Stop();
        using (var server = new HttpListener())
        {
            server.Prefixes.Add("http://127.0.0.1:" + port + "/");
            server.Start();
            var worker = new Thread(() => Serve(server));
            worker.IsBackground = true;
            worker.Start();
            var settings = new Settings {
                QmsBaseUrl = "http://127.0.0.1:" + port,
                QmsTenantCode = "test-tenant", QmsUserName = "test-service", QmsPassword = "test-password",
                GatewayToken = "test-gateway-token", TelemetryPath = "/api/v1/equipment/telemetry",
                RequestTimeoutSeconds = 5, BatchSize = 1, RetryIntervalSeconds = 1,
                MaxRequestBytes = 1024, DisplayColumns = 1, DisplayRows = 1,
                RecordRetentionDays = 1, IdleDisconnectSeconds = 1,
                JingdeAllowedDeviceIps = new HashSet<string>(),
                HosonAllowedDeviceIps = new HashSet<string>(), KstAllowedDeviceIps = new HashSet<string>(),
                QueueFile = Path.Combine(Path.GetTempPath(), "auth-self-test-queue.json"),
                LogFile = Path.Combine(Path.GetTempPath(), "auth-self-test.log")
            };
            var client = new QmsClient(settings);
            var item = new Dictionary<string, object> { { "recordNo", "TEST-1" } };
            client.Upload(new List<Dictionary<string, object>> { item });
            client.Upload(new List<Dictionary<string, object>> { item });
            worker.Join(5000);
            if (serverError != null) throw serverError;
            if (logins != 2 || uploads != 3) throw new Exception("JWT refresh or upload retry count mismatch.");
            string configPath = Path.Combine(Path.GetTempPath(), "QmsGatewayAuthSelfTest-" + Guid.NewGuid() + ".ini");
            try
            {
                settings.Save(configPath);
                if (File.ReadAllText(configPath).Contains("test-password"))
                    throw new Exception("Service password was saved as plaintext.");
                if (Settings.Load(configPath).QmsPassword != "test-password")
                    throw new Exception("Protected service password did not round trip.");
            }
            finally { if (File.Exists(configPath)) File.Delete(configPath); }
        }
        Console.WriteLine("AUTH SELF-TEST PASSED: login=2, upload=3, 401 retry=1");
        return 0;
    }

    private static void Serve(HttpListener server)
    {
        try
        {
            var serializer = new JavaScriptSerializer();
            for (int requestNumber = 0; requestNumber < 5; requestNumber++)
            {
                var context = server.GetContext();
                using (var reader = new StreamReader(context.Request.InputStream, Encoding.UTF8))
                {
                    string body = reader.ReadToEnd();
                    if (context.Request.Url.AbsolutePath == "/api/v1/auth/login")
                    {
                        var credentials = serializer.DeserializeObject(body) as Dictionary<string, object>;
                        if (credentials == null || Convert.ToString(credentials["tenantCode"]) != "test-tenant"
                            || Convert.ToString(credentials["userName"]) != "test-service"
                            || Convert.ToString(credentials["password"]) != "test-password")
                            throw new Exception("Login request is incorrect.");
                        logins++;
                        Write(context, 200, "{\"data\":{\"accessToken\":\"test-token-" + logins
                            + "\",\"expiresIn\":300,\"tenantCode\":\"test-tenant\"}}");
                    }
                    else if (context.Request.Url.AbsolutePath == "/api/v1/equipment/telemetry")
                    {
                        uploads++;
                        string token = uploads == 1 ? "Bearer test-token-1" : "Bearer test-token-2";
                        if (context.Request.Headers["Authorization"] != token
                            || context.Request.Headers["X-Gateway-Token"] != "test-gateway-token"
                            || body.IndexOf("TEST-1", StringComparison.Ordinal) < 0)
                            throw new Exception("Telemetry authentication headers or body are incorrect.");
                        Write(context, uploads == 1 ? 401 : 200, "{\"data\":{\"acceptedCount\":1,\"duplicateCount\":0}}");
                    }
                    else throw new Exception("Unexpected request path.");
                }
            }
        }
        catch (Exception exception) { serverError = exception; }
    }

    private static void Write(HttpListenerContext context, int status, string body)
    {
        byte[] bytes = Encoding.UTF8.GetBytes(body);
        context.Response.StatusCode = status;
        context.Response.ContentType = "application/json";
        context.Response.ContentLength64 = bytes.Length;
        context.Response.OutputStream.Write(bytes, 0, bytes.Length);
        context.Response.Close();
    }
}
