using System;
using System.Collections.Generic;
using System.IO;
using System.Text;
using System.Web.Script.Serialization;

internal static class MesConfig
{
    internal static Dictionary<string, string> Load(string path)
    {
        Dictionary<string, object> source = new JavaScriptSerializer().DeserializeObject(File.ReadAllText(path, Encoding.UTF8)) as Dictionary<string, object>;
        if (source == null || source.Count == 0) throw new ArgumentException("Response config must be a nonempty JSON object.");
        Dictionary<string, string> result = new Dictionary<string, string>(StringComparer.Ordinal);
        foreach (KeyValuePair<string, object> entry in source)
        {
            string response = entry.Value as string;
            ValidateResponse(response);
            result.Add(entry.Key, response);
        }
        return result;
    }

    internal static void ValidateResponse(string response)
    {
        if (string.IsNullOrWhiteSpace(response) ||
            new JavaScriptSerializer().DeserializeObject(response) as Dictionary<string, object> == null)
            throw new ArgumentException("Response must be a JSON object.");
    }

    internal static void Save(string path, Dictionary<string, string> templates)
    {
        if (templates == null || templates.Count == 0) throw new ArgumentException("No response templates to save.");
        foreach (KeyValuePair<string, string> entry in templates) ValidateResponse(entry.Value);
        string temporary = path + ".tmp";
        try
        {
            File.WriteAllText(temporary, new JavaScriptSerializer().Serialize(templates), new UTF8Encoding(false));
            if (File.Exists(path)) File.Replace(temporary, path, null);
            else File.Move(temporary, path);
        }
        finally { if (File.Exists(temporary)) File.Delete(temporary); }
    }

    internal static string Pretty(string json)
    {
        StringBuilder output = new StringBuilder();
        int depth = 0;
        bool quoted = false;
        bool escaped = false;
        for (int i = 0; i < json.Length; i++)
        {
            char ch = json[i];
            if (quoted)
            {
                output.Append(ch);
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') quoted = false;
                continue;
            }
            if (ch == '"') { quoted = true; output.Append(ch); }
            else if (ch == '{' || ch == '[')
            {
                output.Append(ch);
                if (i + 1 < json.Length && (json[i + 1] == '}' || json[i + 1] == ']')) output.Append(json[++i]);
                else { depth++; NewLine(output, depth); }
            }
            else if (ch == '}' || ch == ']') { depth--; NewLine(output, depth); output.Append(ch); }
            else if (ch == ',') { output.Append(ch); NewLine(output, depth); }
            else if (ch == ':') output.Append(": ");
            else if (!char.IsWhiteSpace(ch)) output.Append(ch);
        }
        return output.ToString();
    }

    private static void NewLine(StringBuilder output, int depth)
    {
        output.Append(Environment.NewLine);
        output.Append(' ', depth * 2);
    }
}
