import styles from './SegmentedTabs.module.css'

export interface SegmentedTabItem<T extends string> {
  value: T
  label: string
}

interface SegmentedTabsProps<T extends string> {
  items: readonly SegmentedTabItem<T>[]
  value: T
  onChange: (next: T) => void
  'aria-label'?: string
}

/** 화면 안에서 하위 뷰를 전환하는 세그먼트 탭. 라우팅과 무관한 로컬 상태다. */
export function SegmentedTabs<T extends string>({
  items,
  value,
  onChange,
  'aria-label': ariaLabel,
}: SegmentedTabsProps<T>) {
  return (
    <div className={styles.root} role="tablist" aria-label={ariaLabel}>
      {items.map((item) => {
        const isActive = item.value === value
        return (
          <button
            key={item.value}
            type="button"
            role="tab"
            aria-selected={isActive}
            className={`${styles.tab} ${isActive ? styles.active : ''}`}
            onClick={() => onChange(item.value)}
          >
            {item.label}
          </button>
        )
      })}
    </div>
  )
}
