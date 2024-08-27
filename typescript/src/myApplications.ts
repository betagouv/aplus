document.querySelectorAll<HTMLInputElement>(".trigger-group-filter").forEach((checkbox) => {
  const checkedUrl = checkbox.dataset['onCheckedUrl'];
  const uncheckedUrl = checkbox.dataset['onUncheckedUrl'];
  if (checkedUrl && uncheckedUrl) {
    checkbox.addEventListener("change", () => {
      if (checkbox.checked) {
        window.location.href = checkedUrl;
      } else {
        window.location.href = uncheckedUrl;
      }
    });
  }
});
