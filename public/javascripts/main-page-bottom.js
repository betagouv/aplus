// Note: this file is temporary, everything in main.js should be at the bottom of the page

//
// Transform <select> with SlimSelect
//

var slimSelectClass = "use-slimselect"

Array.from(document.querySelectorAll("." + slimSelectClass)).forEach(function (select) {
  new SlimSelect({ select: select })
});



//
// Welcome Page
//

var welcomePageNewletterCheckboxTagId = "aplus-welcome-page-newsletter-checkbox"
var welcomePageNewletterSubmitTagId = "aplus-welcome-page-newsletter-submit"
var welcomePageNewletterCheckbox = document.querySelector("#" + welcomePageNewletterCheckboxTagId);
if (welcomePageNewletterCheckbox != null) {
  welcomePageNewletterCheckbox.addEventListener('click', function() {
    if (welcomePageNewletterCheckbox.checked) {
      document.querySelector("#" + welcomePageNewletterSubmitTagId).disabled = false;
    } else {
      document.querySelector("#" + welcomePageNewletterSubmitTagId).disabled = true;
    }
  });
}
