const deleteElementClass = "onclick-delete-element";
const checkAllClass = "onclick-check-all";
const uncheckAllClass = "onclick-uncheck-all";
const changeLocationClass = "onclick-change-location";
const printButtonClass = "onclick-print";



document.querySelectorAll<HTMLElement>("." + deleteElementClass).forEach((button) => {
  const selector = button.dataset['selector'];
  if (selector) {
    button.addEventListener("click", () => {
      const element = document.querySelector(selector);
      if (element) {
        element.parentNode?.removeChild(element);
      }
    });
  }
});

document.querySelectorAll<HTMLElement>("." + checkAllClass).forEach((button) => {
  const selector = button.dataset['selector'];
  if (selector) {
    button.addEventListener("click", () => {
      document.querySelectorAll<HTMLInputElement>(selector).forEach((checkbox) => {
        checkbox.checked = true;
        checkbox.parentElement?.classList.add("is-checked");
      });
    });
  }
});

document.querySelectorAll<HTMLElement>("." + uncheckAllClass).forEach((button) => {
  const selector = button.dataset['selector'];
  if (selector) {
    button.addEventListener("click", () => {
      document.querySelectorAll<HTMLInputElement>(selector).forEach((checkbox) => {
        checkbox.checked = false;
        checkbox.parentElement?.classList.remove("is-checked");
      });
    });
  }
});

document.querySelectorAll<HTMLFormElement>("form").forEach((form) => {
  form.querySelectorAll<HTMLInputElement>(".js-on-submit-disabled").forEach((button) => {
    form.addEventListener("submit", () => {
      button.disabled = true;
    });
  });
});

document.querySelectorAll<HTMLElement>("." + changeLocationClass).forEach((button) => {
  const url = button.dataset['location'];
  if (url) {
    button.addEventListener("click", () => {
      window.location.href = url;
    });
  }
});

document.querySelectorAll<HTMLElement>("." + printButtonClass).forEach((button) => {
  button.addEventListener("click", () => {
    window.print();
  });
});
