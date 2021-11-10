// If not found, send back the root
export function findAncestor(element: HTMLElement, check: (e: HTMLElement) => boolean): HTMLElement | null {
  let el: HTMLElement | null = element;
  while ((el = el.parentElement) && !check(el));
  return el;
}

export function debounceAsync<A, R>(
  fn: (args: A) => Promise<R>,
  waitFor: number
): (args: A) => Promise<R> {
  let timeoutID: number;

  if (!Number.isInteger(waitFor)) {
    console.warn("Called debounce without a valid number")
    waitFor = 300;
  }

  const debouncedFn = (args: A): Promise<R> => new Promise((resolve) => {
    if (timeoutID) {
      clearTimeout(timeoutID);
    }
    timeoutID = window.setTimeout(() => resolve(fn(args)), waitFor);
  });
  return debouncedFn;
};
