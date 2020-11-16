// Note: this file is temporary, everything in main.js should be at the bottom of the page
// TODO: The idea is to put everything in main.js in this file, then rename this file as main.js


// Note: `Array.from` does not work in HTMLUnit on NodeList
// so we resort to using this function
function querySelectorAllForEach(selector, exec) {
  var nodes = document.querySelectorAll(selector);
  if (nodes) {
    for (var i = 0; i < nodes.length; i++) {
      exec(nodes[i]);
    }
  }
}



//
// Header ribbon (demo)
//

function setupDemoBanner() {
  if(/localhost|demo/.test(window.location.hostname)) {
    var ribon = document.getElementById("header__ribbon");
    if(ribon) {
      ribon.classList.add("invisible");
    }
    var elements = document.getElementsByClassName("demo-only");
    for (var i = 0; i < elements.length; i++) {
      elements[i].classList.remove("invisible");
    }
  }
}

setupDemoBanner();

//
// Area Change Select
//

function setupChangeAreaSelect() {
  var select = document.getElementById("changeAreaSelect");
  if (select) {
    var currentArea = select.dataset["currentArea"];
    var redirectUrlPrefix = select.dataset["redirectUrlPrefix"];
    select.addEventListener('change', function() {
      var selectedArea = select.value;
      if(selectedArea != currentArea) {
        document.location = redirectUrlPrefix+selectedArea;
      }
    });
  }
}

setupChangeAreaSelect()

//
// Application form
//

function mandatFieldsAreFilled() {
  // 1. Checkbox
  var checkbox = document.getElementById("checkbox-mandat");
  if (checkbox) {
    if (!checkbox.checked) {
      return false;
    }
  }

  // 2. Radios
  var radios = document.querySelectorAll("[id^=mandatType]");
  // Note: same as oneRadioIsChecked = radios.exists(r => r.checked)
  var oneRadioIsChecked = false;
  for (var i = 0; i < radios.length; i++) {
    if (radios[i].checked) {
      oneRadioIsChecked = true;
    }
  }
  if (!oneRadioIsChecked) {
    return false;
  }

  // 3. Date input
  var dateInput = document.getElementById("mandatDate");
  if (!dateInput.value) {
    return false;
  }

  return true;
}

function onMandatFieldChange() {
  if (mandatFieldsAreFilled()) {
    document.querySelector("#review-validation").disabled = false;
  } else {
    document.querySelector("#review-validation").disabled = true;
  }
}

function setupApplicationForm() {

  // Mandat
  var checkboxMandat = document.getElementById("checkbox-mandat");
  var radiosMandat = document.querySelectorAll("[id^=mandatType]");
  var dateMandat = document.getElementById("mandatDate");
  if (checkboxMandat) {
    checkboxMandat.addEventListener('change', onMandatFieldChange);
  }
  for (var i = 0; i < radiosMandat.length; i++) {
    radiosMandat[i].addEventListener('change', onMandatFieldChange);
  }
  if (dateMandat) {
    dateMandat.addEventListener('change', onMandatFieldChange);
    dateMandat.addEventListener('keyup', onMandatFieldChange);
    dateMandat.addEventListener('paste', onMandatFieldChange);
    dateMandat.addEventListener('input', onMandatFieldChange);
  }

}

setupApplicationForm();

//
// SMS Mandat Card
//

var mandatSmsPhoneInputName = "mandat-sms-phone"
var mandatSmsSendButtonId = "mandat-sms-send-button"
var mandatSmsSuccessId = "mandat-sms-success"
var mandatSmsValidationFailedId = "mandat-sms-validation-failed"
var mandatSmsErrorServerId = "mandat-sms-error-server"
var mandatSmsErrorBrowserId = "mandat-sms-error-browser"
var linkedMandatInputId = "linkedMandat"

function setupMandatSmsForm() {
  var inputPrenom = document.getElementById("usagerPrenom");
  var inputNom = document.getElementById("usagerNom");
  var inputBirthDate = document.getElementById("usagerBirthDate");
  var inputPhoneNumber = document.getElementById(mandatSmsPhoneInputName);
  var sendButton = document.getElementById(mandatSmsSendButtonId);
  var successMessage = document.getElementById(mandatSmsSuccessId);
  var validationFailedMessage = document.getElementById(mandatSmsValidationFailedId);
  var serverErrorMessage = document.getElementById(mandatSmsErrorServerId);
  var browserErrorMessage = document.getElementById(mandatSmsErrorBrowserId);
  var linkedMandatInput = document.getElementById(linkedMandatInputId);
  var mandatTypeSmsRadio = document.getElementById("mandatType_sms");

  // Returns null|string
  function validateNonEmptyInput(input) {
    var data = input.value;
    if (data) {
      input.parentNode.classList.remove("is-invalid");
      return data;
    } else {
      input.parentNode.classList.add("is-invalid");
      return null;
    }
  }

  function validatePhoneNumber(input) {
    var data = input.value.replace(/\s/g,'');
    if (/^\d{10}$/.test(data)) {
      inputPhoneNumber.parentNode.classList.remove("is-invalid");
      return data;
    } else {
      inputPhoneNumber.parentNode.classList.add("is-invalid");
      return null;
    }
  }

  function validateForm() {
    var prenom = validateNonEmptyInput(inputPrenom);
    var nom = validateNonEmptyInput(inputNom);
    var birthDate = validateNonEmptyInput(inputBirthDate);
    var phoneNumber = validatePhoneNumber(inputPhoneNumber);
    var isValid = prenom && nom && birthDate && phoneNumber;

    return {
      isValid: isValid,
      data: {
        prenom: prenom,
        nom: nom,
        birthDate: birthDate,
        phoneNumber: phoneNumber
      }
    };
  }

  function sendForm(data, callbackSuccess, callbackServerError, callbackBrowserError) {
    try {
      var xhr = new XMLHttpRequest();
      xhr.onreadystatechange = function() {
        if (xhr.readyState == XMLHttpRequest.DONE) {   // XMLHttpRequest.DONE == 4
          try {
            if (Math.floor(xhr.status / 100) === 2) {
              callbackSuccess(JSON.parse(xhr.responseText));
            } else {
              callbackServerError();
            }
          } catch (error) {
            console.error(error);
            callbackBrowserError(error);
          }
        }
      };
      xhr.open("POST", "/mandats/sms", true);
      xhr.setRequestHeader("Content-Type", "application/json");
      // Play recommends putting the CSRF token, even for AJAX request
      // and cites browser plugins as culprits
      // https://www.playframework.com/documentation/2.8.x/ScalaCsrf#Plays-CSRF-protection
      var token = document.querySelector("input[name=csrfToken]").value;
      xhr.setRequestHeader("Csrf-Token", token);
      xhr.send(JSON.stringify(data));
    } catch (error) {
      console.error(error);
      callbackBrowserError(error);
    }
  }

  if (sendButton) {
    sendButton.onclick = function(event) {
      event.preventDefault();
      successMessage.classList.add("hidden");
      validationFailedMessage.classList.add("hidden");
      serverErrorMessage.classList.add("hidden");
      browserErrorMessage.classList.add("hidden");
      var formData = validateForm();
      if (formData.isValid) {
        sendButton.disabled = true;
        sendForm(
          formData.data,
          // Success
          function(mandat) {
            var link = successMessage.querySelector("a");
            link.href = "/mandats/" + mandat.id;
            linkedMandatInput.value = mandat.id;
            successMessage.classList.remove("hidden");
            // Note: mandatTypeSmsRadio.checked = true does not show the radio as checked
            mandatTypeSmsRadio.click();
          },
          // Server error (= logged by Sentry)
          function() {
            // Wait 30s
            setTimeout(function() { sendButton.disabled = false; }, 30000);
            serverErrorMessage.classList.remove("hidden");
          },
          // Browser error (= not logged by Sentry)
          function() {
            // Wait 30s
            setTimeout(function() { sendButton.disabled = false; }, 30000);
            browserErrorMessage.classList.remove("hidden");
          }
        );
      } else {
        validationFailedMessage.classList.remove("hidden");
      }
    }
  }
}

setupMandatSmsForm();

//
// Dialog
//

var dialogDeleteGroupId = "dialog-delete-group";
var dialogDeleteGroupButtonShowId = "dialog-delete-group-show";
var dialogDeleteGroupButtonCancelId = "dialog-delete-group-cancel";
var dialogDeleteGroupButtonConfirmId = "dialog-delete-group-confirm";
function setupDialog() {
  var dialog = document.getElementById(dialogDeleteGroupId);

  if (dialog) {
    if (!dialog.showModal) {
      dialogPolyfill.registerDialog(dialog);
    }

    querySelectorAllForEach(
        "#" + dialogDeleteGroupButtonCancelId,
        function (element) {
          element.addEventListener('click', function(event) {
            dialog.close();
          });
        }
    );

    querySelectorAllForEach(
        "#" + dialogDeleteGroupButtonShowId,
        function (element) {
          element.addEventListener('click', function(event) {
            dialog.showModal();
          });
        }
    );

    querySelectorAllForEach(
        "#" + dialogDeleteGroupButtonConfirmId,
        function (element) {
          element.addEventListener('click', function(event) {
            var uuid = element.dataset.uuid;
            var url = jsRoutes.controllers.GroupController.deleteUnusedGroupById(uuid).url;
            window.location = url;
          });
        }
    );
  }
}


setupDialog();

//
// Transform <select> with SlimSelect
//

var slimSelectClass = "use-slimselect";

Array.from(document.querySelectorAll("." + slimSelectClass)).forEach(function (select) {
  new SlimSelect({ select: select, selectByGroup: true, closeOnSelect: false});
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
