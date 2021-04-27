// If not found, send back the root
export function findAncestor(el: HTMLElement, check: (e: HTMLElement) => boolean): HTMLElement {
  while ((el = el.parentElement) && !check(el));
  return el;
}
