import type { PageResponse } from '../api/types'

/** Previous and next over a PageResponse, with the count spelled out. */
export default function Pager<T>({
  page,
  data,
  onPage,
}: {
  page: number
  data: PageResponse<T>
  onPage: (next: number) => void
}) {
  if (data.totalElements === 0) {
    return null
  }

  return (
    <div className="pager">
      <button type="button" onClick={() => onPage(page - 1)} disabled={page <= 0}>
        Previous
      </button>
      <span className="pager__count">
        {data.totalElements} in total, page {data.page + 1} of {Math.max(data.totalPages, 1)}
      </span>
      <button
        type="button"
        onClick={() => onPage(page + 1)}
        disabled={page + 1 >= data.totalPages}
      >
        Next
      </button>
    </div>
  )
}
