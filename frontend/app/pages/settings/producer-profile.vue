<script setup lang="ts">
import Button from 'primevue/button'
import { useToast } from 'primevue/usetoast'
import { useAuthStore } from '~/stores/auth'
import { useProducerProfile } from '~/composables/api/useProducerProfile'

const { t } = useI18n()
const router = useRouter()
const toast = useToast()
const authStore = useAuthStore()

// SME_ADMIN guard
if (authStore.role !== 'SME_ADMIN') {
  await navigateTo('/dashboard')
}

const {
  profile,
  pending,
  saveError,
  savedSuccessfully,
  fetchProfile,
  saveProfile,
} = useProducerProfile()

// Form state
const form = reactive({
  legalName: '',
  addressCountryCode: 'HU',
  addressCity: '',
  addressPostalCode: '',
  addressStreetName: '',
  addressStreetType: '',
  addressHouseNumber: '',
  kshStatisticalNumber: '',
  companyRegistrationNumber: '',
  contactName: '',
  contactTitle: '',
  contactCountryCode: 'HU',
  contactPostalCode: '',
  contactCity: '',
  contactStreetName: '',
  contactPhone: '',
  contactEmail: '',
  okirClientId: null as number | null,
  isManufacturer: true,
  isIndividualPerformer: true,
  isSubcontractor: false,
  isConcessionaire: false,
})

// KSH pattern validation: NNNNNNNN-TTTT-GGG-MM
const kshPattern = /^\d{8}-\d{4}-\d{3}-\d{2}$/
const kshValid = computed(() =>
  !form.kshStatisticalNumber || kshPattern.test(form.kshStatisticalNumber),
)

onMounted(async () => {
  await fetchProfile()
  if (profile.value) {
    Object.assign(form, {
      legalName: profile.value.legalName ?? '',
      addressCountryCode: profile.value.addressCountryCode ?? 'HU',
      addressCity: profile.value.addressCity ?? '',
      addressPostalCode: profile.value.addressPostalCode ?? '',
      addressStreetName: profile.value.addressStreetName ?? '',
      addressStreetType: profile.value.addressStreetType ?? '',
      addressHouseNumber: profile.value.addressHouseNumber ?? '',
      kshStatisticalNumber: profile.value.kshStatisticalNumber ?? '',
      companyRegistrationNumber: profile.value.companyRegistrationNumber ?? '',
      contactName: profile.value.contactName ?? '',
      contactTitle: profile.value.contactTitle ?? '',
      contactCountryCode: profile.value.contactCountryCode ?? 'HU',
      contactPostalCode: profile.value.contactPostalCode ?? '',
      contactCity: profile.value.contactCity ?? '',
      contactStreetName: profile.value.contactStreetName ?? '',
      contactPhone: profile.value.contactPhone ?? '',
      contactEmail: profile.value.contactEmail ?? '',
      okirClientId: profile.value.okirClientId ?? null,
      isManufacturer: profile.value.isManufacturer ?? true,
      isIndividualPerformer: profile.value.isIndividualPerformer ?? true,
      isSubcontractor: profile.value.isSubcontractor ?? false,
      isConcessionaire: profile.value.isConcessionaire ?? false,
    })
  }
})

async function handleSave() {
  if (!kshValid.value) return
  try {
    await saveProfile(form)
    toast.add({
      severity: 'success',
      summary: t('settings.producerProfile.savedSuccess'),
      life: 3000,
    })
  }
  catch {
    toast.add({
      severity: 'error',
      summary: t('settings.producerProfile.saveError'),
      life: 5000,
    })
  }
}
</script>

<template>
  <div class="max-w-3xl mx-auto py-8 px-4">
    <div class="flex items-center justify-between mb-6">
      <h1 class="text-2xl font-bold text-slate-800">
        {{ t('settings.producerProfile.title') }}
      </h1>
      <Button
        :label="t('common.back')"
        icon="pi pi-arrow-left"
        severity="secondary"
        outlined
        @click="router.back()"
      />
    </div>

    <div v-if="pending" class="flex justify-center py-12">
      <i class="pi pi-spin pi-spinner text-4xl text-slate-400" aria-hidden="true" />
    </div>

    <form v-else class="space-y-6" @submit.prevent="handleSave">
      <!-- Legal identity section -->
      <section class="bg-white border border-slate-200 rounded-lg p-6">
        <h2 class="text-base font-semibold text-slate-700 mb-4">
          {{ t('settings.producerProfile.sections.identity') }}
        </h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div class="md:col-span-2">
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.legalName') }} *</label>
            <input v-model="form.legalName" type="text" required
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-legal-name" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.kshStatisticalNumber') }}</label>
            <input v-model="form.kshStatisticalNumber" type="text" placeholder="12345678-1234-123-01"
              :class="['w-full border rounded px-3 py-2 text-sm',
                kshValid ? 'border-slate-300' : 'border-red-500']"
              data-testid="field-ksh-number" />
            <p v-if="!kshValid" class="text-xs text-red-600 mt-1">
              {{ t('settings.producerProfile.validation.kshPattern') }}
            </p>
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.companyRegistrationNumber') }}</label>
            <input v-model="form.companyRegistrationNumber" type="text" placeholder="01-09-12345678"
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-company-reg-number" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.okirClientId') }}</label>
            <input v-model.number="form.okirClientId" type="number"
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-okir-client-id" />
          </div>
        </div>
      </section>

      <!-- Registered address section -->
      <section class="bg-white border border-slate-200 rounded-lg p-6">
        <h2 class="text-base font-semibold text-slate-700 mb-4">
          {{ t('settings.producerProfile.sections.address') }}
        </h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.addressCity') }} *</label>
            <input v-model="form.addressCity" type="text" required
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-address-city" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.addressPostalCode') }} *</label>
            <input v-model="form.addressPostalCode" type="text" required
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-address-postal-code" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.addressStreetName') }} *</label>
            <input v-model="form.addressStreetName" type="text" required
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-address-street-name" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.addressStreetType') }}</label>
            <input v-model="form.addressStreetType" type="text"
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-address-street-type" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.addressHouseNumber') }}</label>
            <input v-model="form.addressHouseNumber" type="text"
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-address-house-number" />
          </div>
        </div>
      </section>

      <!-- Contact person section -->
      <section class="bg-white border border-slate-200 rounded-lg p-6">
        <h2 class="text-base font-semibold text-slate-700 mb-4">
          {{ t('settings.producerProfile.sections.contact') }}
        </h2>
        <div class="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.contactName') }} *</label>
            <input v-model="form.contactName" type="text" required
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-contact-name" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.contactTitle') }} *</label>
            <input v-model="form.contactTitle" type="text" required
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-contact-title" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.contactPhone') }} *</label>
            <input v-model="form.contactPhone" type="tel" required
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-contact-phone" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.contactEmail') }} *</label>
            <input v-model="form.contactEmail" type="email" required
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-contact-email" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.contactCity') }}</label>
            <input v-model="form.contactCity" type="text"
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-contact-city" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.contactPostalCode') }}</label>
            <input v-model="form.contactPostalCode" type="text"
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-contact-postal-code" />
          </div>
          <div>
            <label class="block text-sm text-slate-600 mb-1">{{ t('settings.producerProfile.fields.contactStreetName') }}</label>
            <input v-model="form.contactStreetName" type="text"
              class="w-full border border-slate-300 rounded px-3 py-2 text-sm"
              data-testid="field-contact-street-name" />
          </div>
        </div>
      </section>

      <!-- EPR role flags -->
      <section class="bg-white border border-slate-200 rounded-lg p-6">
        <h2 class="text-base font-semibold text-slate-700 mb-4">
          {{ t('settings.producerProfile.sections.roles') }}
        </h2>
        <div class="space-y-2">
          <label class="flex items-center gap-2 cursor-pointer">
            <input v-model="form.isManufacturer" type="checkbox"
              class="w-4 h-4" data-testid="field-is-manufacturer" />
            <span class="text-sm text-slate-700">{{ t('settings.producerProfile.fields.isManufacturer') }}</span>
          </label>
          <label class="flex items-center gap-2 cursor-pointer">
            <input v-model="form.isIndividualPerformer" type="checkbox"
              class="w-4 h-4" data-testid="field-is-individual-performer" />
            <span class="text-sm text-slate-700">{{ t('settings.producerProfile.fields.isIndividualPerformer') }}</span>
          </label>
          <label class="flex items-center gap-2 cursor-pointer">
            <input v-model="form.isSubcontractor" type="checkbox"
              class="w-4 h-4" data-testid="field-is-subcontractor" />
            <span class="text-sm text-slate-700">{{ t('settings.producerProfile.fields.isSubcontractor') }}</span>
          </label>
          <label class="flex items-center gap-2 cursor-pointer">
            <input v-model="form.isConcessionaire" type="checkbox"
              class="w-4 h-4" data-testid="field-is-concessionaire" />
            <span class="text-sm text-slate-700">{{ t('settings.producerProfile.fields.isConcessionaire') }}</span>
          </label>
        </div>
      </section>

      <!-- Save button -->
      <div class="flex justify-end gap-3">
        <Button
          type="submit"
          :label="t('settings.producerProfile.saveButton')"
          icon="pi pi-save"
          :loading="pending"
          :disabled="!kshValid"
          data-testid="save-producer-profile-button"
        />
      </div>
    </form>
  </div>
</template>
