import type { ProcessMaster } from '../types/qms.ts'

export function finishedTestProcess(processes: readonly ProcessMaster[]): ProcessMaster | undefined {
  return processes.find((process) => process.processCode === 'FQC'
    || process.processName === 'FQC' || process.processName === '成品测试')
}

export function responsibleDepartmentForProcess(process: ProcessMaster | undefined, code: string): string {
  return process?.responsibleDepartment
    || (code === 'FQC' || process?.processName === 'FQC' || process?.processName === '成品测试' ? '测试' : '')
}
