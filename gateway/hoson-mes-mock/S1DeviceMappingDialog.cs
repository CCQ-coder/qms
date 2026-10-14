using System;
using System.Drawing;
using System.Globalization;
using System.Windows.Forms;

internal sealed class S1DeviceMappingDialog : Form
{
    private readonly S1MqttSettings settings;
    private readonly ListBox mappings = new ListBox();
    private readonly TextBox source = new TextBox(), product = new TextBox(), serial = new TextBox(), client = new TextBox(), user = new TextBox(), password = new TextBox();
    private int selectedIndex = -1;

    internal S1DeviceMappingDialog(S1MqttSettings configuredSettings)
    {
        settings = configuredSettings;
        Text = "S1设备映射";
        ClientSize = new Size(650, 355);
        StartPosition = FormStartPosition.CenterParent;
        FormBorderStyle = FormBorderStyle.FixedDialog;
        MaximizeBox = false;
        password.UseSystemPasswordChar = true;
        TableLayoutPanel layout = new TableLayoutPanel();
        layout.Dock = DockStyle.Fill;
        layout.Padding = new Padding(12);
        layout.ColumnCount = 2;
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Absolute, 180));
        layout.ColumnStyles.Add(new ColumnStyle(SizeType.Percent, 100));
        layout.RowCount = 8;
        layout.RowStyles.Add(new RowStyle(SizeType.Percent, 100));
        for (int i = 1; i < 8; i++) layout.RowStyles.Add(new RowStyle(SizeType.Absolute, 30));
        Controls.Add(layout);
        mappings.Dock = DockStyle.Fill;
        mappings.SelectedIndexChanged += delegate { SelectMapping(); };
        layout.SetColumnSpan(mappings, 2);
        layout.Controls.Add(mappings, 0, 0);
        Add(layout, "来源设备号:", source, 1);
        Add(layout, "S1产品ID:", product, 2);
        Add(layout, "S1序列号:", serial, 3);
        Add(layout, "MQTT Client ID:", client, 4);
        Add(layout, "MQTT用户名:", user, 5);
        Add(layout, "MQTT密码:", password, 6);
        FlowLayoutPanel buttons = new FlowLayoutPanel();
        Button save = new Button(); save.Text = "新增/更新"; save.Click += delegate { Apply(); };
        Button close = new Button(); close.Text = "完成"; close.DialogResult = DialogResult.OK;
        buttons.Controls.Add(save); buttons.Controls.Add(close);
        layout.SetColumnSpan(buttons, 2);
        layout.Controls.Add(buttons, 0, 7);
        RefreshList();
    }

    private static void Add(TableLayoutPanel layout, string label, TextBox input, int row)
    {
        Label title = new Label(); title.Text = label; title.TextAlign = ContentAlignment.MiddleRight; title.Dock = DockStyle.Fill;
        input.Dock = DockStyle.Fill;
        layout.Controls.Add(title, 0, row); layout.Controls.Add(input, 1, row);
    }

    private void RefreshList()
    {
        mappings.Items.Clear();
        foreach (S1DeviceMapping mapping in settings.Devices) mappings.Items.Add(mapping.SourceDeviceCode + " → " + mapping.SerialNumber);
        if (selectedIndex >= 0 && selectedIndex < mappings.Items.Count) mappings.SelectedIndex = selectedIndex;
    }

    private void SelectMapping()
    {
        selectedIndex = mappings.SelectedIndex;
        if (selectedIndex < 0) return;
        S1DeviceMapping mapping = settings.Devices[selectedIndex];
        source.Text = mapping.SourceDeviceCode; product.Text = mapping.ProductId.ToString(CultureInfo.InvariantCulture);
        serial.Text = mapping.SerialNumber; client.Text = mapping.ClientId; user.Text = mapping.UserName; password.Text = "";
    }

    private void Apply()
    {
        long productId;
        if (!long.TryParse(product.Text.Trim(), NumberStyles.None, CultureInfo.InvariantCulture, out productId) || productId <= 0)
        {
            MessageBox.Show(this, "S1产品ID必须为正整数。", "配置无效", MessageBoxButtons.OK, MessageBoxIcon.Warning); return;
        }
        S1DeviceMapping mapping = selectedIndex >= 0 ? settings.Devices[selectedIndex] : new S1DeviceMapping();
        mapping.SourceDeviceCode = source.Text.Trim(); mapping.ProductId = productId; mapping.SerialNumber = serial.Text.Trim(); mapping.ClientId = client.Text.Trim(); mapping.UserName = user.Text.Trim();
        if (!string.IsNullOrEmpty(password.Text)) mapping.ProtectedPassword = S1MqttSettingsStore.ProtectPassword(password.Text);
        if (selectedIndex < 0) { settings.Devices.Add(mapping); selectedIndex = settings.Devices.Count - 1; }
        try { S1MqttSettingsStore.Validate(settings); RefreshList(); }
        catch (Exception error) { MessageBox.Show(this, error.Message, "配置无效", MessageBoxButtons.OK, MessageBoxIcon.Warning); }
    }
}
