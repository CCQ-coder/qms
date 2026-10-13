<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { qmsApi } from '@/api/qms'

const AUTO_NCR_KEY = 'quality.auto-create-ncr'
const PRODUCT_CODE_VALIDATION_KEY = 'master-data.product-code.validation-enabled'
const PRODUCT_CODE_PATTERN_KEY = 'master-data.product-code.pattern'
const DEFAULT_PRODUCT_CODE_PATTERN = '[A-Za-z0-9][A-Za-z0-9._-]{0,39}'

const loading = ref(false)
const savingProductRule = ref(false)
const savingAutoNcr = ref(false)
const productCodeValidationEnabled = ref(false)
const productCodePattern = ref(DEFAULT_PRODUCT_CODE_PATTERN)
const productCodeExample = ref('PCB-4L-001')
const autoCreateNcr = ref(false)

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
    const [autoNcrParameter, validationParameter, patternParameter] = await Promise.all([
      qmsApi.getTenantParameter(AUTO_NCR_KEY),
      qmsApi.getTenantParameter(PRODUCT_CODE_VALIDATION_KEY),
      qmsApi.getTenantParameter(PRODUCT_CODE_PATTERN_KEY)
    ])
    autoCreateNcr.value = autoNcrParameter.value === 'true'
    productCodeValidationEnabled.value = validationParameter.value === 'true'
    productCodePattern.value = patternParameter.value || DEFAULT_PRODUCT_CODE_PATTERN
  } catch {
    ElMessage.error('系统参数读取失败')
  } finally {
    loading.value = false
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
        <p>参数仅影响当前租户。修改料号规则后，新建产品资料会立即按新规则校验。</p>
      </div>
    </div>

    <el-card shadow="never" class="content-card">
      <template #header><div class="card-header"><span>料号规则</span><span class="helper-text">替代独立的料号规则配置页面</span></div></template>
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
  </section>
</template>
