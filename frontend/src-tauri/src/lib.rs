use serde::{Deserialize, Serialize};
use std::fs;
use std::io::ErrorKind;
use std::path::PathBuf;
use tauri::{AppHandle, Manager};

const DEFAULT_API_BASE_URL: &str = "http://127.0.0.1:8080";

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct DesktopConnection {
    api_base_url: String,
}

impl Default for DesktopConnection {
    fn default() -> Self {
        Self {
            api_base_url: DEFAULT_API_BASE_URL.to_owned(),
        }
    }
}

fn normalize_api_base_url(value: &str) -> Result<String, String> {
    let normalized = value.trim().trim_end_matches('/');
    if !(normalized.starts_with("http://") || normalized.starts_with("https://"))
        || normalized.split_whitespace().count() != 1
        || normalized.len() <= "http://".len()
    {
        return Err("服务地址必须是有效的 HTTP 或 HTTPS 地址。".to_owned());
    }
    Ok(normalized.to_owned())
}

fn connection_file(app: &AppHandle) -> Result<PathBuf, String> {
    let directory = app
        .path()
        .app_config_dir()
        .map_err(|error| format!("无法确定桌面端配置目录：{error}"))?;
    fs::create_dir_all(&directory).map_err(|error| format!("无法创建桌面端配置目录：{error}"))?;
    Ok(directory.join("connection.json"))
}

#[tauri::command]
fn read_desktop_connection(app: AppHandle) -> Result<DesktopConnection, String> {
    let file = connection_file(&app)?;
    let content = match fs::read_to_string(file) {
        Ok(content) => content,
        Err(error) if error.kind() == ErrorKind::NotFound => return Ok(DesktopConnection::default()),
        Err(error) => return Err(format!("无法读取桌面端连接配置：{error}")),
    };
    let connection: DesktopConnection = serde_json::from_str(&content)
        .map_err(|error| format!("桌面端连接配置格式无效：{error}"))?;
    Ok(DesktopConnection {
        api_base_url: normalize_api_base_url(&connection.api_base_url)?,
    })
}

#[tauri::command]
fn save_desktop_connection(app: AppHandle, api_base_url: String) -> Result<DesktopConnection, String> {
    let connection = DesktopConnection {
        api_base_url: normalize_api_base_url(&api_base_url)?,
    };
    let serialized = serde_json::to_string_pretty(&connection)
        .map_err(|error| format!("无法序列化桌面端连接配置：{error}"))?;
    fs::write(connection_file(&app)?, serialized)
        .map_err(|error| format!("无法保存桌面端连接配置：{error}"))?;
    Ok(connection)
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![read_desktop_connection, save_desktop_connection])
        .run(tauri::generate_context!())
        .expect("运行 PCB QMS 桌面端时发生错误");
}
