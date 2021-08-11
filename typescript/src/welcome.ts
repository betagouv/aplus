//
// Welcome Page
//

const welcomePageNewletterCheckboxTagId = "aplus-welcome-page-newsletter-checkbox";
const welcomePageNewletterSubmitTagId = "aplus-welcome-page-newsletter-submit";

const welcomePageNewletterCheckbox =
  document.querySelector<HTMLInputElement>("#" + welcomePageNewletterCheckboxTagId);
if (welcomePageNewletterCheckbox) {
  welcomePageNewletterCheckbox.addEventListener('click', () => {
    const button = document.querySelector<HTMLButtonElement>("#" + welcomePageNewletterSubmitTagId);
    if (welcomePageNewletterCheckbox.checked) {
      button.disabled = false;
    } else {
      button.disabled = true;
    }
  });
}
