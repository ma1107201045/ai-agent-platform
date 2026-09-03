import request from './request'
import type { NodeTypeSchema } from './types'

/** 编排器元数据 API（节点 Schema 单源化，对应后端 /api/orchestrator/**） */
export const orchestratorApi = {
  /** 全部节点类型 Schema：字段 / 默认值 / 必填 / 连线约束 */
  nodeTypes() {
    return request.get<never, NodeTypeSchema[]>('/orchestrator/node-types')
  }
}
