using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Web.Script.Serialization;

internal sealed class MesExchange
{
    internal DateTime Time;
    internal string Method;
    internal string ClientIp;
    internal string RequestPath;
    internal string RequestBody;
    internal string ResponseBody;
    internal int Status;
}

internal sealed class HosonMesServer
{
    private const int MaxRequestBytes = 1024 * 1024;
    private HttpListener listener;
    private Thread acceptThread;
    private volatile Dictionary<string, string> responses;

    internal event Action<MesExchange> ExchangeReceived;
    internal bool IsRunning { get { return listener != null; } }

    internal void Start(string ipText, int port, Dictionary<string, string> templates)
    {
        if (IsRunning) throw new InvalidOperationException("Service is already running.");
        IPAddress ip;
        if (!IPAddress.TryParse(ipText, out ip) || ip.AddressFamily != AddressFamily.InterNetwork)
            throw new ArgumentException("Enter a valid IPv4 address (0.0.0.0 listens on all interfaces).");
        if (port < 1 || port > 65535) throw new ArgumentException("Port must be between 1 and 65535.");
        UpdateResponses(templates);

        HttpListener next = new HttpListener();
        next.Prefixes.Add("http://" + (ip.Equals(IPAddress.Any) ? "+" : ip.ToString()) + ":" + port + "/");
        try { next.Start(); }
        catch { next.Close(); throw; }
        listener = next;
        acceptThread = new Thread(delegate() { Accept(next); });
        acceptThread.IsBackground = true;
        acceptThread.Start();
    }

    internal void UpdateResponses(Dictionary<string, string> templates)
    {
        if (templates == null || templates.Count == 0) throw new ArgumentException("Response config must be a nonempty JSON object.");
        Dictionary<string, string> copy = new Dictionary<string, string>(templates, StringComparer.Ordinal);
        foreach (KeyValuePair<string, string> entry in copy)
        {
            if (string.IsNullOrWhiteSpace(entry.Key) || string.IsNullOrWhiteSpace(entry.Value))
                throw new ArgumentException("Each method needs a response template.");
        }
        responses = copy;
    }

    internal void Stop()
    {
        HttpListener active = listener;
        if (active == null) return;
        listener = null;
        active.Stop();
        active.Close();
        if (Thread.CurrentThread != acceptThread) acceptThread.Join(2000);
    }

    private void Accept(HttpListener active)
    {
        while (active.IsListening)
        {
            HttpListenerContext context;
            try { context = active.GetContext(); }
            catch (HttpListenerException) { break; }
            catch (ObjectDisposedException) { break; }
            ThreadPool.QueueUserWorkItem(delegate { Handle(context); });
        }
    }

    private void Handle(HttpListenerContext context)
    {
        MesExchange item = new MesExchange();
        item.Time = DateTime.Now;
        item.ClientIp = context.Request.RemoteEndPoint == null ? "unknown" : context.Request.RemoteEndPoint.Address.ToString();
        item.RequestPath = context.Request.Url == null ? "" : context.Request.Url.AbsolutePath;
        item.RequestBody = "";
        item.Method = "";
        item.Status = 200;
        try
        {
            if (item.RequestPath.TrimEnd('/') != "/api/mes/invoke")
            {
                item.Status = 404;
                item.ResponseBody = Error("Unknown path");
            }
            else if (context.Request.HttpMethod != "POST")
            {
                item.Status = 405;
                item.ResponseBody = Error("Only POST is supported");
            }
            else
            {
                item.RequestBody = ReadBody(context.Request.InputStream);
                Dictionary<string, object> request;
                try { request = new JavaScriptSerializer().DeserializeObject(item.RequestBody) as Dictionary<string, object>; }
                catch (ArgumentException) { request = null; }
                catch (InvalidOperationException) { request = null; }
                if (request == null)
                {
                    item.Status = 400;
                    item.ResponseBody = Error("Invalid JSON request");
                }
                else
                {
                    object value;
                    item.Method = request.TryGetValue("method", out value) ? value as string : "";
                    if (string.IsNullOrWhiteSpace(item.Method))
                    {
                        item.Status = 400;
                        item.ResponseBody = Error("Missing method field");
                    }
                    else
                    {
                        string response;
                        if (responses.TryGetValue(item.Method, out response)) item.ResponseBody = response;
                        else
                        {
                            item.Status = 404;
                            item.ResponseBody = Error("Unknown method");
                        }
                    }
                }
            }
        }
        catch (InvalidDataException)
        {
            item.Status = 413;
            item.ResponseBody = Error("Request body exceeds 1 MB");
        }
        catch (Exception)
        {
            item.Status = 500;
            item.ResponseBody = Error("Server error");
        }
        finally
        {
            byte[] bytes = Encoding.UTF8.GetBytes(item.ResponseBody ?? "");
            try
            {
                context.Response.StatusCode = item.Status;
                context.Response.ContentType = "application/json; charset=utf-8";
                context.Response.Headers["Access-Control-Allow-Origin"] = "*";
                context.Response.ContentLength64 = bytes.Length;
                context.Response.OutputStream.Write(bytes, 0, bytes.Length);
                context.Response.Close();
            }
            catch (Exception) { }
            Action<MesExchange> handler = ExchangeReceived;
            if (handler != null) handler(item);
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
}
