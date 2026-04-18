import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useMaterialTemplatePicker } from './useMaterialTemplatePicker'

const apiFetchMock = vi.fn()
vi.mock('~/composables/api/useApi', () => ({
  useApi: () => ({ apiFetch: apiFetchMock })
}))

describe('useMaterialTemplatePicker', () => {
  beforeEach(() => {
    apiFetchMock.mockReset()
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('list', () => {
    it('fetches templates and applies page/size slicing', async () => {
      const templates = Array.from({ length: 7 }, (_, i) => ({
        id: `id-${i}`,
        name: `T${i}`,
        baseWeightGrams: 10,
        kfCode: null,
        verified: false,
        recurring: true,
        createdAt: '2026-04-17T00:00:00Z',
        updatedAt: '2026-04-17T00:00:00Z',
        overrideKfCode: null,
        overrideReason: null,
        confidence: null,
        feeRate: null,
        materialClassification: null
      }))
      apiFetchMock.mockResolvedValueOnce(templates)

      const { list } = useMaterialTemplatePicker()
      const page1 = await list(0, 3)
      expect(page1).toHaveLength(3)
      expect(page1[0]!.id).toBe('id-0')
      expect(apiFetchMock).toHaveBeenCalledWith('/api/v1/epr/materials')

      apiFetchMock.mockResolvedValueOnce(templates)
      const page2 = await list(1, 3)
      expect(page2).toHaveLength(3)
      expect(page2[0]!.id).toBe('id-3')
    })

    it('clamps negative page and oversized size', async () => {
      apiFetchMock.mockResolvedValueOnce([])
      const { list } = useMaterialTemplatePicker()
      await list(-5, 10_000)
      // No error — clamp keeps args sane. Result is empty, but the call completed.
      expect(apiFetchMock).toHaveBeenCalledOnce()
    })
  })

  describe('search', () => {
    it('filters by substring, case-insensitive', async () => {
      apiFetchMock.mockResolvedValueOnce([
        { id: '1', name: 'PET palack', baseWeightGrams: 25,
          kfCode: null, verified: false, recurring: true,
          createdAt: '', updatedAt: '', overrideKfCode: null, overrideReason: null,
          confidence: null, feeRate: null, materialClassification: null },
        { id: '2', name: 'Karton doboz', baseWeightGrams: 50,
          kfCode: null, verified: false, recurring: true,
          createdAt: '', updatedAt: '', overrideKfCode: null, overrideReason: null,
          confidence: null, feeRate: null, materialClassification: null }
      ])
      const { search } = useMaterialTemplatePicker()
      const results = await search('pet')
      expect(results).toHaveLength(1)
      expect(results[0]!.name).toBe('PET palack')
    })

    it('returns empty for blank term without hitting the API', async () => {
      const { search } = useMaterialTemplatePicker()
      const results = await search('   ')
      expect(results).toEqual([])
      expect(apiFetchMock).not.toHaveBeenCalled()
    })
  })

  describe('createDraft', () => {
    it('posts a draft with defaulted baseWeightGrams when omitted', async () => {
      apiFetchMock.mockResolvedValueOnce({
        id: 'new-id', name: 'New', baseWeightGrams: 1,
        kfCode: null, verified: false, recurring: true,
        createdAt: '', updatedAt: '', overrideKfCode: null, overrideReason: null,
        confidence: null, feeRate: null, materialClassification: null
      })
      const { createDraft } = useMaterialTemplatePicker()
      const created = await createDraft({ name: 'New' })
      expect(created.id).toBe('new-id')
      expect(apiFetchMock).toHaveBeenCalledWith('/api/v1/epr/materials', {
        method: 'POST',
        body: { name: 'New', baseWeightGrams: 1, recurring: true }
      })
    })

    it('trims whitespace on the name', async () => {
      apiFetchMock.mockResolvedValueOnce({
        id: 'x', name: 'Box', baseWeightGrams: 5,
        kfCode: null, verified: false, recurring: true,
        createdAt: '', updatedAt: '', overrideKfCode: null, overrideReason: null,
        confidence: null, feeRate: null, materialClassification: null
      })
      const { createDraft } = useMaterialTemplatePicker()
      await createDraft({ name: '  Box  ', baseWeightGrams: 5 })
      expect(apiFetchMock).toHaveBeenCalledWith('/api/v1/epr/materials', expect.objectContaining({
        body: expect.objectContaining({ name: 'Box', baseWeightGrams: 5 })
      }))
    })

    it('throws on blank name', async () => {
      const { createDraft } = useMaterialTemplatePicker()
      await expect(createDraft({ name: '   ' })).rejects.toThrow('name is required')
      expect(apiFetchMock).not.toHaveBeenCalled()
    })

    it('forwards kfCode and materialClassification when provided', async () => {
      apiFetchMock.mockResolvedValueOnce({
        id: 'draft-id', name: 'PET', baseWeightGrams: 1,
        kfCode: '11010101', verified: false, recurring: true,
        createdAt: '', updatedAt: '', overrideKfCode: null, overrideReason: null,
        confidence: null, feeRate: null, materialClassification: 'PLASTIC'
      })
      const { createDraft } = useMaterialTemplatePicker()
      await createDraft({ name: 'PET', kfCode: '11010101', materialClassification: 'PLASTIC' })
      expect(apiFetchMock).toHaveBeenCalledWith('/api/v1/epr/materials', {
        method: 'POST',
        body: {
          name: 'PET',
          baseWeightGrams: 1,
          recurring: true,
          kfCode: '11010101',
          materialClassification: 'PLASTIC'
        }
      })
    })

    it('omits kfCode and materialClassification when not provided', async () => {
      apiFetchMock.mockResolvedValueOnce({
        id: 'bare-id', name: 'Box', baseWeightGrams: 1,
        kfCode: null, verified: false, recurring: true,
        createdAt: '', updatedAt: '', overrideKfCode: null, overrideReason: null,
        confidence: null, feeRate: null, materialClassification: null
      })
      const { createDraft } = useMaterialTemplatePicker()
      await createDraft({ name: 'Box' })
      const body = apiFetchMock.mock.calls[0]![1]!.body
      expect(body).not.toHaveProperty('kfCode')
      expect(body).not.toHaveProperty('materialClassification')
    })
  })
})
