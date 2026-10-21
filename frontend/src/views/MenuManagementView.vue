<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { navigationMenuDefinitions } from '@/navigation/menu'
import { refreshMenuAccess } from '@/navigation/menu'
import type { AdministrationItem } from '@/types/qms'

type MenuRow = AdministrationItem & {
  menuType: 'GROUP' | 'PAGE'
  displayLabel: string
  path?: string
  children?: MenuRow[]
}

const loading = ref(false)
const savingMenuCode = ref('')
const menuItems = ref<AdministrationItem[]>([])

const menuRows = computed<MenuRow[]>(() => {
  const itemByCode = new Map(menuItems.value.map((item) => [item.itemCode, item]))
  const groupByCode = new Map<string, MenuRow>()
  const rootRows: MenuRow[] = []

  for (const definition of navigationMenuDefinitions) {
    const item = itemByCode.get(definition.code)
    if (!item) continue
    const row: MenuRow = {
      ...item,
      menuType: definition.parentCode ? 'PAGE' : 'GROUP',
      displayLabel: definition.label,
      path: definition.path,
      children: definition.parentCode ? undefined : []
    }
    if (!definition.parentCode) {
      groupByCode.set(definition.code, row)
      rootRows.push(row)
    } else {
      groupByCode.get(definition.parentCode)?.children?.push(row)
    }
  }
  return rootRows
})

async function load() {
  loading.value = true
  try {
    menuItems.value = await qmsApi.listAdministrationItems('MENU')
  } catch {
    ElMessage.error('菜单配置读取失败，请确认当前账号为租户管理员')
  } finally {
    loading.value = false
  }
}

async function changeStatus(item: MenuRow, active: boolean) {
  savingMenuCode.value = item.itemCode
  try {
    await qmsApi.updateAdministrationItem('MENU', item.id, {
      itemCode: item.itemCode,
      itemName: item.itemName,
      parentCode: item.parentCode,
      description: item.description,
      status: active ? 'ACTIVE' : 'INACTIVE',
      sortOrder: item.sortOrder,
      attributes: item.attributes
    })
    await Promise.all([load(), refreshMenuAccess()])
    ElMessage.success(active ? '菜单已显示' : '菜单已隐藏')
  } catch {
    ElMessage.error('菜单显示状态保存失败')
  } finally {
    savingMenuCode.value = ''
  }
}

onMounted(() => { void load() })
</script>

<template>
  <section class="page-stack" v-loading="loading">
    <div class="hero-card compact-hero">
      <div>
        <p class="eyebrow">系统管理</p>
        <h2>菜单管理</h2>
        <p>按菜单分组控制前台导航显示。隐藏分组会同时隐藏其全部子菜单；隐藏页面只影响该页面入口。</p>
      </div>
    </div>

    <el-card shadow="never" class="content-card">
      <template #header>
        <div class="card-header">
          <span>菜单显示配置</span>
          <el-button @click="load">刷新</el-button>
        </div>
      </template>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 18px">
        角色权限中的“可见菜单”会在此处的显示状态基础上继续筛选；两项都允许时，用户才会看到对应页面。
      </el-alert>
      <el-table :data="menuRows" row-key="itemCode" :tree-props="{ children: 'children' }" default-expand-all empty-text="暂无菜单配置">
        <el-table-column prop="displayLabel" label="菜单名称" min-width="210" />
        <el-table-column label="类型" width="100"><template #default="scope"><el-tag :type="scope.row.menuType === 'GROUP' ? 'primary' : 'info'" size="small">{{ scope.row.menuType === 'GROUP' ? '分组' : '页面' }}</el-tag></template></el-table-column>
        <el-table-column prop="itemCode" label="菜单编码" min-width="190" />
        <el-table-column label="路由地址" min-width="210"><template #default="scope">{{ scope.row.path || '—' }}</template></el-table-column>
        <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
        <el-table-column label="显示状态" width="185" fixed="right">
          <template #default="scope">
            <div class="table-status-control">
              <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'info'" size="small">{{ scope.row.status === 'ACTIVE' ? '显示' : '隐藏' }}</el-tag>
              <el-switch :model-value="scope.row.status === 'ACTIVE'" :loading="savingMenuCode === scope.row.itemCode" @change="changeStatus(scope.row, Boolean($event))" />
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>
