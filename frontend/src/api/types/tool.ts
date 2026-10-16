/** 外部连接器（tool_connector）：http=HTTP API / mysql=MySQL 数据库 */
export interface ToolConnector {
  id: number
  tenantId: number
  /** 连接器名称（英文标识符，生成 HTTP 工具时作为工具名） */
  name: string
  description?: string
  /** http / mysql */
  type: 'http' | 'mysql'
  /** http：API 地址；mysql：JDBC URL */
  url?: string
  /** http：请求方式 */
  method?: string
  /** http：额外请求头（JSON 对象） */
  headers?: string
  /** 鉴权：none / bearer / basic */
  authType?: 'none' | 'bearer' | 'basic'
  authToken?: string
  authUsername?: string
  authPassword?: string
  /** 状态：0禁用 1启用 */
  status: number
  createTime?: string
  updateTime?: string
}

/** 插件市场工具模板 */
export interface ToolTemplate {
  key: string
  /** 工具名称（英文标识符，安装后为 app_agent_tool.name） */
  name: string
  description: string
  /** basic 通用 / text 文本处理 / web 网络数据 */
  category: string
  /** http / code */
  type: 'http' | 'code'
  method?: string
  url?: string
  code?: string
  /** 参数 JSON Schema */
  parameters?: string
  /** 是否已安装 */
  installed: boolean
}
