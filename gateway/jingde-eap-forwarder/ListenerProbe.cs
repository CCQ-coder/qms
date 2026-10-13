using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Web.Script.Serialization;

internal static class ListenerProbe
{
    private static int Main(string[] args)
    {
        bool interactive = args.Length == 0;
        try
        {
            if (!interactive && args.Length != 4)
                throw new ArgumentException("Usage: ListenerProbe.exe <IP> <port> <jingde|hoson|kst> <equipment-code>");

            string address = interactive ? Ask("Listener IP") : args[0];
            string portText = interactive ? Ask("Listener port") : args[1];
            string protocol = (interactive ? Ask("Protocol (jingde/hoson/kst)") : args[2]).ToLowerInvariant();
            string equipment = interactive ? Ask("Mapped test equipment code") : args[3];
            IPAddress ip;
            int port;
            if (!IPAddress.TryParse(address, out ip)) throw new ArgumentException("Enter a valid IP address.");
            if (!int.TryParse(portText, NumberStyles.None, CultureInfo.InvariantCulture, out port) || port < 1 || port > 65535)
                throw new ArgumentException("Port must be between 1 and 65535.");
            if (string.IsNullOrWhiteSpace(equipment)) throw new ArgumentException("Equipment code is required.");

            string path;
            object payload;
            string id = Guid.NewGuid().ToString("N");
            if (protocol == "jingde")
            {
                path = "/RestAPI/EQPStatusChangeReport";
                payload = new {
                    Header = new { MessageName = "EQPStatusChangeReport", TransactionID = DateTime.Now.ToString("yyyyMMddHHmmss", CultureInfo.InvariantCulture) + id.Substring(0, 6), UserID = "listener-probe" },
                    Body = new { EquipmentID = equipment, CurrentAxis = "0", Status = "1" },
                    Result = new { Code = 1 }
                };
            }
            else if (protocol == "hoson")
            {
                path = "/";
                payload = new {
                    id = id,
                    timestamp = (DateTime.UtcNow.Ticks - new DateTime(1970, 1, 1).Ticks) / 10000,
                    version = "1.0.0.1",
                    method = "login",
                    data = new { device_code = equipment, user_name = "listener-probe" }
                };
            }
            else if (protocol == "kst")
            {
                path = "/Status";
                payload = new {
                    ID = id, MachineNO = equipment, Status = "WAIT",
                    MachineTime = DateTime.Now.ToString("yyyy-MM-dd HH:mm:ss", CultureInfo.InvariantCulture),
                    UserName = "listener-probe", LotNO = "TEST", CBARCODE = "TEST"
                };
            }
            else throw new ArgumentException("Protocol must be jingde, hoson, or kst.");

            string host = ip.AddressFamily == AddressFamily.InterNetworkV6 ? "[" + ip + "]" : ip.ToString();
            string url = "http://" + host + ":" + port + path;
            byte[] body = new UTF8Encoding(false).GetBytes(new JavaScriptSerializer().Serialize(payload));
            Console.WriteLine("POST " + url);
            Console.WriteLine(Encoding.UTF8.GetString(body));
            HttpWebRequest request = (HttpWebRequest)WebRequest.Create(url);
            request.Method = "POST";
            request.ContentType = "application/json; charset=utf-8";
            request.ContentLength = body.Length;
            request.Timeout = 5000;
            request.ReadWriteTimeout = 5000;
            request.Proxy = null;
            using (Stream stream = request.GetRequestStream()) stream.Write(body, 0, body.Length);
            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())
            using (StreamReader reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
            {
                string text = reader.ReadToEnd();
                Console.WriteLine("HTTP " + (int)response.StatusCode + " " + text);
                Dictionary<string, object> result = new JavaScriptSerializer().DeserializeObject(text) as Dictionary<string, object>;
                bool accepted = false;
                if (result != null && protocol == "jingde")
                {
                    object value;
                    Dictionary<string, object> status = result.TryGetValue("Result", out value) ? value as Dictionary<string, object> : null;
                    accepted = status != null && status.TryGetValue("Code", out value) && Convert.ToInt32(value, CultureInfo.InvariantCulture) == 1;
                }
                else if (result != null)
                {
                    object value;
                    accepted = result.TryGetValue("success", out value) && value is bool && (bool)value;
                }
                if (!accepted) throw new Exception("Listener did not confirm acceptance.");
                Console.WriteLine("Accepted into listener queue. Check the listener window/log for QMS forwarding.");
                return 0;
            }
        }
        catch (WebException error)
        {
            HttpWebResponse response = error.Response as HttpWebResponse;
            if (response == null) Console.Error.WriteLine("Connection failed: " + error.Message);
            else
            {
                using (response)
                using (StreamReader reader = new StreamReader(response.GetResponseStream(), Encoding.UTF8))
                    Console.Error.WriteLine("HTTP " + (int)response.StatusCode + " " + reader.ReadToEnd());
            }
            return 1;
        }
        catch (Exception error)
        {
            Console.Error.WriteLine("Test failed: " + error.Message);
            return 1;
        }
        finally
        {
            if (interactive)
            {
                Console.WriteLine("Press Enter to close.");
                Console.ReadLine();
            }
        }
    }

    private static string Ask(string label)
    {
        Console.Write(label + ": ");
        return (Console.ReadLine() ?? "").Trim();
    }
}
