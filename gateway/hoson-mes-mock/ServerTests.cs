using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

internal static class ServerTests
{
    private static int Main()
    {
        int port;
        TcpListener reservation = new TcpListener(IPAddress.Loopback, 0);
        reservation.Start();
        port = ((IPEndPoint)reservation.LocalEndpoint).Port;
        reservation.Stop();

        HosonMesServer server = new HosonMesServer();
        MesExchange last = null;
        using (ManualResetEvent received = new ManualResetEvent(false))
        {
            server.ExchangeReceived += delegate(MesExchange item) { last = item; received.Set(); };
            try
            {
                server.Start("127.0.0.1", port, new Dictionary<string, string> { { "login", "{\"version\":1}" } });
                Assert(Post(port, "{\"method\":\"login\"}") == "{\"version\":1}", "initial response");
                Assert(received.WaitOne(3000), "request log emitted");
                Assert(last.Method == "login" && last.RequestBody.Contains("login") && last.ResponseBody.Contains("version"), "log details");

                server.UpdateResponses(new Dictionary<string, string> { { "login", "{\"version\":2}" } });
                Assert(Post(port, "{\"method\":\"login\"}") == "{\"version\":2}", "edited response used without restart");
            }
            finally { server.Stop(); }
        }
        string configPath = Path.Combine(Path.GetTempPath(), "hoson-mock-test-" + Guid.NewGuid().ToString("N") + ".json");
        try
        {
            MesConfig.Save(configPath, new Dictionary<string, string> { { "login", "{\"data\":{\"token\":\"A\"}}" } });
            Assert(MesConfig.Load(configPath)["login"].Contains("\"A\""), "saved config reloads");
            Assert(MesConfig.Pretty("{\"x\":\"a,b\",\"items\":[1,2]}").Contains("\"a,b\""), "formatter keeps commas inside strings");
            Assert(MesConfig.Pretty("{\"x\":1}").Contains("\n"), "response editor gets readable lines");
        }
        finally { if (File.Exists(configPath)) File.Delete(configPath); }
        Console.WriteLine("Server update and request log checks passed.");
        return 0;
    }

    private static string Post(int port, string body)
    {
        HttpWebRequest request = (HttpWebRequest)WebRequest.Create("http://127.0.0.1:" + port + "/api/mes/invoke");
        request.Method = "POST";
        request.ContentType = "application/json";
        byte[] bytes = Encoding.UTF8.GetBytes(body);
        request.ContentLength = bytes.Length;
        using (Stream output = request.GetRequestStream()) output.Write(bytes, 0, bytes.Length);
        using (WebResponse response = request.GetResponse())
        using (StreamReader input = new StreamReader(response.GetResponseStream())) return input.ReadToEnd();
    }

    private static void Assert(bool condition, string message)
    {
        if (!condition) throw new Exception("Failed: " + message);
    }
}
