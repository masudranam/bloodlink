import { useNavigate } from './routerContext'

/**
 * An anchor that navigates without reloading.
 *
 * A real href, so the link can be middle-clicked, copied and opened in a new tab
 * like any other link. The click handler only takes over the plain left click.
 */
export default function Link({
  to,
  className,
  children,
}: {
  to: string
  className?: string
  children: React.ReactNode
}) {
  const navigate = useNavigate()

  return (
    <a
      href={to}
      className={className}
      onClick={(event) => {
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) {
          return
        }
        event.preventDefault()
        navigate(to)
      }}
    >
      {children}
    </a>
  )
}
