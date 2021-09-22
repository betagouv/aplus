// If not found, send back the root
export function findAncestor(element: HTMLElement, check: (e: HTMLElement) => boolean): HTMLElement | null {
  let el: HTMLElement | null = element;
  while ((el = el.parentElement) && !check(el));
  return el;
}
