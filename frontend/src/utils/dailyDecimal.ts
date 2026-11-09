/** DECIMAL(18,6) quantities remain exact across transfer previews. */
export function scaledQuantity(value: string | number | null | undefined): bigint | null {
  const text = String(value ?? '').trim()
  if (!/^\d{1,12}(\.\d{1,6})?$/.test(text)) return null
  const [whole, fraction = ''] = text.split('.')
  return BigInt(whole) * 1000000n + BigInt(fraction.padEnd(6, '0'))
}
export function decimalText(value: bigint): string {
  const sign = value < 0n ? '-' : ''; const amount = value < 0n ? -value : value
  const fraction = String(amount % 1000000n).padStart(6, '0').replace(/0+$/, '')
  return sign + String(amount / 1000000n) + (fraction ? '.' + fraction : '')
}
export function quantityText(value: string | number | null | undefined): string {
  if (value == null || value === '') return '-'
  const scaled = scaledQuantity(value)
  if (scaled == null) return String(value)
  const [whole, fraction] = decimalText(scaled).split('.')
  return whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',') + (fraction ? '.' + fraction : '')
}
