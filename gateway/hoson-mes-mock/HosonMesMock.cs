using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;

internal static class HosonMesMock
{
    private const int MaxRequestBytes = 1024 * 1024;
    private static volatile bool stopping;

    private static int Main(string[] args)
    {
        try
        {
            if (args.Length != 0 && args.Length != 2)
                throw new ArgumentException("Usage: HosonMesMock.exe [listen IPv4] [port]");
            string ipText = args.Length == 2 ? args[0] : Ask("Listen IPv4", "127.0.0.1");
            string portText = args.Length == 2 ? args[1] : Ask("Port", "6680");
            IPAddress ip;
            int port;
            if (!IPAddress.TryParse(ipText, out ip) || ip.AddressFamily != AddressFamily.InterNetwork)
                throw new ArgumentException("Enter a valid IPv4 address (0.0.0.0 listens on all interfaces).");
            if (!int.TryParse(portText, NumberStyles.None, CultureInfo.InvariantCulture, out port) || port < 1 || port > 65535)
                throw new ArgumentException("Port must be between 1 and 65535.");

            string configPath = Path.Combine(AppDomain.CurrentDomain.BaseDirectory, "mes_server_config.json");
            Dictionary<string, object> templates = new JavaScriptSerializer().DeserializeObject(File.ReadAllText(configPath, Encoding.UTF8)) as Dictionary<string, object>;
            if (templates == null || templates.Count == 0) throw new ArgumentException("Response config must be a nonempty JSON object.");
            foreach (KeyValuePair<string, object> entry in templates)
            {
                string response = entry.Value as string;
                if (string.IsNullOrWhiteSpace(response) || new JavaScriptSerializer().DeserializeObject(response) as Dictionary<string, object> == null)
                    throw new ArgumentException("Invalid response template for method " + entry.Key);
            }

            string host = ip.Equals(IPAddress.Any) ? "+" : ip.ToString();
            using (HttpListener listener = new HttpListener())
            {
                listener.Prefixes.Add("http://" + host + ":" + port + "/");
                listener.Start();
                Console.CancelKeyPress += delegate(object sender, ConsoleCancelEventArgs e) { e.Cancel = true; stopping = true; listener.Stop(); };
                Console.WriteLine("Hoson MES mock listening on " + ip + ":" + port + "/api/mes/invoke");
                Console.WriteLine("Response templates: " + configPath);
                Console.WriteLine("Press Ctrl+C to stop.");
                while (!stopping)
                {
                    HttpListenerContext context;
                    try { context = listener.GetContext(); }
                    catch (HttpListenerException) { if (stopping) break; throw; }
                    catch (ObjectDisposedException) { if (stopping) break; throw; }
                    HttpListenerContext request = context;
                    ThreadPool.QueueUserWorkItem(delegate { Handle(request, templates); });
                }
            }
            return 0;
        }
        catch (Exception error)
        {
            Console.Error.WriteLine("Hoson MES mock failed: " + error.Message);
            return 1;
        }
    }

    private static void Handle(HttpListenerContext context, Dictionary<string, object> templates)
    {
        string remote = "unknown";
        string method = "";
        int status = 200;
        string response = "";
        try
        {
            IPEndPoint endpoint = context.Request.RemoteEndPoint;
            if (endpoint != null) remote = endpoint.Address.ToString();
            if (context.Request.Url == null || context.Request.Url.AbsolutePath.TrimEnd('/') != "/api/mes/invoke")
            {
                status = 404;
                response = Error("Unknown path");
            }
            else if (context.Request.HttpMethod != "POST")
            {
                status = 405;
                response = Error("Only POST is supported");
            }
            else
            {
                string body = ReadBody(context.Request.InputStream);
                Dictionary<string, object> request;
                try { request = new JavaScriptSerializer().DeserializeObject(body) as Dictionary<string, object>; }
                catch (ArgumentException) { request = null; }
                catch (InvalidOperationException) { request = null; }
                if (request == null)
                {
                    status = 400;
                    response = Error("Invalid JSON request");
                }
                else
                {
                    object value;
                    method = request.TryGetValue("method", out value) ? value as string : "";
                    if (string.IsNullOrWhiteSpace(method))
                    {
                        status = 400;
                        response = Error("Missing method field");
                    }
                    else if (templates.TryGetValue(method, out value)) response = (string)value;
                    else
                    {
                        status = 404;
                        response = Error("Unknown method");
                    }
                }
            }
        }
        catch (InvalidDataException)
        {
            status = 413;
            response = Error("Request body exceeds 1 MB");
        }
        catch (Exception error)
        {
            status = 500;
            response = Error("Server error");
            Console.Error.WriteLine(error);
        }
        finally
        {
            byte[] bytes = Encoding.UTF8.GetBytes(response);
            try
            {
                context.Response.StatusCode = status;
                context.Response.ContentType = "application/json; charset=utf-8";
                context.Response.Headers["Access-Control-Allow-Origin"] = "*";
                context.Response.ContentLength64 = bytes.Length;
                context.Response.OutputStream.Write(bytes, 0, bytes.Length);
                context.Response.Close();
            }
            catch (Exception error) { Console.Error.WriteLine("Could not send response: " + error.Message); }
            Console.WriteLine(DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss", CultureInfo.InvariantCulture) + " " + remote + " " + method + " HTTP " + status);
        }
    }

    private static string ReadBody(Stream input)
    {
        using (MemoryStream buffer = new MemoryStream())
        {
            byte[] chunk = new byte[8192];
            int count;
            while ((count = input.Read(chunk, 0, chunk.Length)) > 0)
            {
                if (buffer.Length + count > MaxRequestBytes) throw new InvalidDataException();
                buffer.Write(chunk, 0, count);
            }
            return new UTF8Encoding(false, true).GetString(buffer.ToArray());
        }
    }

    private static string Error(string message)
    {
        return new JavaScriptSerializer().Serialize(new { success = false, message = message, error_code = -1, method = "" });
    }

    private static string Ask(string label, string fallback)
    {
        Console.Write(label + " [" + fallback + "]: ");
        string value = Console.ReadLine();
        return string.IsNullOrWhiteSpace(value) ? fallback : value.Trim();
    }
}
