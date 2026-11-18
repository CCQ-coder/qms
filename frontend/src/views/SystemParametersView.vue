<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { qmsApi } from '@/api/qms'
import { BROWSER_TITLE_KEY, DEFAULT_APP_TITLE, setBrowserTitle, setSidebarTitle, SIDEBAR_TITLE_KEY } from '@/layout/appBrand'

const AUTO_NCR_KEY = 'quality.auto-create-ncr'
const PRODUCT_CODE_VALIDATION_KEY = 'master-data.product-code.validation-enabled'
const PRODUCT_CODE_PATTERN_KEY = 'master-data.product-code.pattern'
const STANDARD_BOARDS_KEY = 'quality.finished-test.standard-boards'
const DEFAULT_PRODUCT_CODE_PATTERN = '[A-Za-z0-9][A-Za-z0-9._-]{0,39}'

const loading = ref(false)
const savingProductRule = ref(false)
const savingAutoNcr = ref(false)
const savingBrand = ref<'browser' | 'sidebar' | ''>('')
const browserTitle = ref(DEFAULT_APP_TITLE)
const sidebarTitle = ref(DEFAULT_APP_TITLE)
const savingStandardBoards = ref(false)
const productCodeValidationEnabled = ref(false)
const productCodePattern = ref(DEFAULT_PRODUCT_CODE_PATTERN)
const productCodeExample = ref('PCB-4L-001')
const autoCreateNcr = ref(false)
const standardBoards = ref('FZV1')

const productCodeMatches = computed(() => {
  if (!productCodeValidationEnabled.value) return true
  try {
    return new RegExp(`^(?:${productCodePattern.value})$`).test(productCodeExample.value)
  } catch {
    return false
  }
})

async function load() {
  loading.value = true
  try {
    const [autoNcrParameter, validationParameter, patternParameter, browserParameter, sidebarParameter, standardBoardsParameter] = await Promise.all([
      qmsApi.getTenantParameter(AUTO_NCR_KEY),
      qmsApi.getTenantParameter(PRODUCT_CODE_VALIDATION_KEY),
      qmsApi.getTenantParameter(PRODUCT_CODE_PATTERN_KEY),
      qmsApi.getTenantParameter(BROWSER_TITLE_KEY),
      qmsApi.getTenantParameter(SIDEBAR_TITLE_KEY),
      qmsApi.getTenantParameter(STANDARD_BOARDS_KEY)
    ])
    autoCreateNcr.value = autoNcrParameter.value === 'true'
    productCodeValidationEnabled.value = validationParameter.value === 'true'
    productCodePattern.value = patternParameter.value || DEFAULT_PRODUCT_CODE_PATTERN
    browserTitle.value = browserParameter.value || DEFAULT_APP_TITLE
    sidebarTitle.value = sidebarParameter.value || DEFAULT_APP_TITLE
    standardBoards.value = standardBoardsParameter.value || 'FZV1'
  } catch {
    ElMessage.error('系统参数读取失败')
  } finally {
    loading.value = false
  }
}

async function saveBrandTitle(kind: 'browser' | 'sidebar') {
  const title = (kind === 'browser' ? browserTitle.value : sidebarTitle.value).trim()
  if (!title || title.length > 40) {
    ElMessage.warning('标题须为 1 到 40 个字符')
    return
  }
  savingBrand.value = kind
  try {
    await qmsApi.updateTenantParameter(kind === 'browser' ? BROWSER_TITLE_KEY : SIDEBAR_TITLE_KEY, title)
    if (kind === 'browser') {
      browserTitle.value = title
      setBrowserTitle(title)
    } else {
      sidebarTitle.value = title
      setSidebarTitle(title)
    }
    ElMessage.success('标题已保存')
  } catch {
    ElMessage.error('标题保存失败；请确认当前账号具有租户管理员权限')
  } finally {
    savingBrand.value = ''
  }
}

async function saveStandardBoards() {
  const numbers = [...new Set(standardBoards.value.split(/[,，\s]+/).map(value => value.trim().toUpperCase()).filter(Boolean))]
  if (!numbers.length) {
    ElMessage.warning('请至少填写一个标准板厂内编号')
    return
  }
  const value = numbers.join('\n')
  if (value.length > 1000) {
    ElMessage.warning('标准板编号总长度不能超过 1000 字符')
    return
  }
  savingStandardBoards.value = true
  try {
    await qmsApi.updateTenantParameter(STANDARD_BOARDS_KEY, value)
    standardBoards.value = value
    ElMessage.success('标准板编号已保存')
  } catch {
    ElMessage.error('标准板编号保存失败；请确认当前账号具有租户管理员权限')
  } finally {
    savingStandardBoards.value = false
  }
}

async function saveProductCodeRule() {
  const pattern = productCodePattern.value.trim()
  if (productCodeValidationEnabled.value) {
    if (!pattern) {
      ElMessage.warning('启用料号校验时必须填写匹配规则')
      return
    }
    try {
      new RegExp(`^(?:${pattern})$`)
    } catch {
      ElMessage.warning('料号匹配规则不是有效的正则表达式')
      return
    }
  }
  savingProductRule.value = true
  try {
    await Promise.all([
      qmsApi.updateTenantParameter(PRODUCT_CODE_VALIDATION_KEY, String(productCodeValidationEnabled.value)),
      qmsApi.updateTenantParameter(PRODUCT_CODE_PATTERN_KEY, pattern || DEFAULT_PRODUCT_CODE_PATTERN)
    ])
    ElMessage.success('料号规则已保存')
  } catch {
    ElMessage.error('料号规则保存失败；请确认当前账号具有租户管理员权限')
  } finally {
    savingProductRule.value = false
  }
}

async function updateAutoNcr(value: string | number | boolean) {
  const next = value === true
  const previous = !next
  savingAutoNcr.value = true
  try {
    await qmsApi.updateTenantParameter(AUTO_NCR_KEY, String(next))
    ElMessage.success(next ? '已开启不合格自动创建不良单' : '已关闭不合格自动创建不良单')
  } catch {
    autoCreateNcr.value = previous
    ElMessage.error('参数更新失败；请确认当前账号具有租户管理员权限')
  } finally {
    savingAutoNcr.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="page-stack system-parameters-page" v-loading="loading">
    <div class="hero-card compact-hero">
      <div>
        <p class="eyebrow">租户级配置</p>
        <h2>系统参数</h2>
      </div>
    </div>

    <el-card shadow="never" class="content-card compact-card">
      <template #header><div class="card-header"><span>系统标题</span></div></template>
      <el-form label-position="top" class="system-parameter-form">
        <el-form-item label="浏览器标签标题"><el-input v-model="browserTitle" maxlength="40" show-word-limit placeholder="PCB QMS" /></el-form-item>
        <el-button type="primary" :loading="savingBrand === 'browser'" @click="saveBrandTitle('browser')">保存浏览器标题</el-button>
        <el-form-item label="左侧栏标题" style="margin-top: 18px"><el-input v-model="sidebarTitle" maxlength="40" show-word-limit placeholder="PCB QMS" /></el-form-item>
        <el-button type="primary" :loading="savingBrand === 'sidebar'" @click="saveBrandTitle('sidebar')">保存左侧栏标题</el-button>
      </el-form>
    </el-card>

    <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>料号规则</span></div></template>
      <div class="system-parameter-section">
        <div class="parameter-row">
          <div><strong>启用料号格式校验</strong><p>开启后，新增产品资料的料号必须匹配下方规则。</p></div>
          <el-switch v-model="productCodeValidationEnabled" />
        </div>
        <el-form label-position="top" class="system-parameter-form">
          <el-form-item label="料号匹配规则（正则表达式）">
            <el-input v-model="productCodePattern" :disabled="!productCodeValidationEnabled" placeholder="例如：[A-Za-z0-9][A-Za-z0-9._-]{0,39}" />
            <div class="field-help">默认允许字母、数字、连字符、下划线和小数点，长度 1–40 位。规则不需要额外添加 ^ 和 $。</div>
          </el-form-item>
          <el-form-item label="即时校验示例">
            <el-input v-model="productCodeExample" placeholder="输入料号验证规则" />
            <div class="rule-preview" :class="productCodeMatches ? 'rule-preview--pass' : 'rule-preview--fail'">{{ productCodeValidationEnabled ? (productCodeMatches ? '符合当前料号规则' : '不符合当前料号规则') : '当前未启用料号格式校验' }}</div>
          </el-form-item>
          <el-button type="primary" :loading="savingProductRule" @click="saveProductCodeRule">保存料号规则</el-button>
        </el-form>
      </div>
    </el-card>

    <el-card shadow="never" class="content-card compact-card">
      <template #header><div class="card-header"><span>质量流程</span></div></template>
      <div class="parameter-row">
        <div><strong>检验不合格时自动创建不良品处置单</strong><p>关闭时由检测人员在不良品模块中按需要发起处置。</p></div>
        <el-switch v-model="autoCreateNcr" :loading="savingAutoNcr" @change="updateAutoNcr" />
      </div>
    </el-card>

    <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>成品测试标准板</span></div></template>
      <div class="system-parameter-section">
        <p>按厂内编号匹配，每行一个，可配置多个。标准板不参与生产指令绑定和成品测试统计，原始上报记录仍保留。</p>
        <el-input v-model="standardBoards" type="textarea" :rows="5" :maxlength="1000" show-word-limit placeholder="例如：FZV1" />
        <el-button type="primary" :loading="savingStandardBoards" style="margin-top: 16px" @click="saveStandardBoards">保存标准板编号</el-button>
      </div>
    </el-card>
  </section>
</template>
