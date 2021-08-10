const formExitAlertClass = "aplus-protected-form";

let aplusFormHasBeenSubmitted = false;
const message = "Merci de confirmer la fermeture de cette page, les données saisies ne seront pas enregistrées.";

// https://developer.mozilla.org/en-US/docs/Web/API/WindowEventHandlers/onbeforeunload
function setupOnbeforeunload(event: Event): string {
  // Cancel the event (HTML Specification)
  event.preventDefault();
  return message;
}

function removeOnbeforeunload() {
  window.onbeforeunload = null;
}

function setupOneProtectedForm(form: Element) {
  if (form == null) {
    return;
  }

  form.querySelectorAll("input, textarea, select").forEach((elem) => {
    elem.addEventListener("input", () => {
      if (!aplusFormHasBeenSubmitted) {
        // do not use `window.addEventListener("beforeunload", setupOnbeforeunload)`
        // it won't work...
        window.onbeforeunload = setupOnbeforeunload;
      } else {
        window.onbeforeunload = null;
      }
    });
  });

  form.addEventListener("submit", () => {
    aplusFormHasBeenSubmitted = true;
    removeOnbeforeunload();
  });
}

// Uses the class "aplus-protected-form"
// Note: It is not possible to stop the popup appearing in the function of onbeforeunload
// setting window.onbeforeunload = null, will just stop the popup the *next* time.
document.querySelectorAll("." + formExitAlertClass).forEach(setupOneProtectedForm);
