const deleteElementClass = "onclick-delete-element";
const checkAllClass = "onclick-check-all";
const uncheckAllClass = "onclick-uncheck-all";
const disableOnSubmitClass = "onsubmit-disable";


document.querySelectorAll<HTMLElement>("." + deleteElementClass).forEach((button) => {
  const selector = button.dataset.selector;
  button.addEventListener("click", () => {
    const element = document.querySelector(selector);
    element.parentNode.removeChild(element);
  });
});

document.querySelectorAll<HTMLElement>("." + checkAllClass).forEach((button) => {
  const selector = button.dataset.selector;
  button.addEventListener("click", () => {
    document.querySelectorAll<HTMLInputElement>(selector).forEach((checkbox) => {
      checkbox.checked = true;
      checkbox.parentElement.classList.add("is-checked");
    });
  });
});

document.querySelectorAll<HTMLElement>("." + uncheckAllClass).forEach((button) => {
  const selector = button.dataset.selector;
  button.addEventListener("click", () => {
    document.querySelectorAll<HTMLInputElement>(selector).forEach((checkbox) => {
      checkbox.checked = false;
      checkbox.parentElement.classList.remove("is-checked");
    });
  });
});

document.querySelectorAll<HTMLButtonElement>("." + disableOnSubmitClass).forEach((button) => {
  button.addEventListener("submit", () => {
    button.disabled = true;
  });
});