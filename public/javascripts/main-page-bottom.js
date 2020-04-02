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
// Application form
//

var createApplicationFormId = "create-application-form";

// <input type="checkbox">
var applicationFormGroupCheckboxClass = "aplus-application-form-group";

function applicationFormAddSelectedGroupInfos(groupName) {
  function checkDoesNotExist(name) {
    var existingInfos = document.querySelectorAll("input[name^='infos[']");
    for (var i = 0; i < existingInfos.length; i++) {
      if (existingInfos[i].name.indexOf(name) !== -1) {
        return false;
      }
    }
    return true;
  }

  // CAF / CNAF => Identifiant CAF
  // CPAM / MSA / CNAV => Numéro de sécurité sociale
  if (/CN?AF(\s|$)/i.test(groupName)) {
    var name = "Identifiant CAF";
    if (checkDoesNotExist(name)) {
      addOptionalInfoRow(name, "");
    }
  } else if (/(CPAM|MSA|CNAV)(\s|$)/i.test(groupName)) {
    var name = "Numéro de sécurité sociale";
    if (checkDoesNotExist(name)) {
      addOptionalInfoRow(name, "");
    }
  }
}

function showHideUsers(sender) {
  var thead = sender.parentNode.parentNode.parentNode.parentNode;
  var tbody = thead.nextElementSibling;
  var inputs = tbody.querySelectorAll("[type=checkbox]");
  if(sender.checked) {
    for(var i = 0; i < inputs.length; i++){
      var input = inputs[i];
      input.checked = true;
      input.parentNode.classList.add("is-checked");
      componentHandler.upgradeElements(input);
    }
    tbody.classList.remove("invisible");
    applicationFormAddSelectedGroupInfos(sender.value);
  } else {
    for(var i = 0; i < inputs.length; i++){
      var input = inputs[i];
      input.checked = false;
      input.parentNode.classList.remove("is-checked");
      componentHandler.upgradeElements(input);
    }
    tbody.classList.add("invisible");
  }

}


// <select> of info type
var applicationFormUserInfosTypesSelectId = "aplus-application-form-user-infos-types-select-id";
// <button>
var applicationFormUserInfosAddButtonClass = "aplus-application-form-user-infos-add-button";
var applicationFormUserInfosRemoveButtonClass = "aplus-application-form-user-infos-remove-button";
// <input>
var applicationFormUserInfosInputClass = "aplus-application-form-user-infos-input";


function newTypeSelected() {
  var select = document.getElementById(applicationFormUserInfosTypesSelectId);
  var otherTypeElement = document.getElementById("other-type");
  if(select.value === "Autre") {
    otherTypeElement.value = "";
    otherTypeElement.parentNode.classList.remove("invisible");
    //componentHandler.upgradeElements(otherTypeElem.parentNode.parentNode.getElementsByTagName("*"));
  } else {
    otherTypeElement.parentNode.classList.add("invisible");
    otherTypeElement.value = select.value;
  }
  if(select.value !== "") {
    document.getElementById("other-type-value").parentNode.classList.remove("invisible");
    document.getElementById("add-infos__ok-button").classList.remove("invisible");
  }
  componentHandler.upgradeDom();
}

function removeInfo(elem) {
  var row = elem.parentNode;
  row.parentNode.removeChild(row);
}

function addOptionalInfoRow(infoName, infoValue) {
  var newNode = document.createElement("div");
  newNode.innerHTML = ''+infoName+' (facultatif)'+'<br> \
                       <div class="mdl-textfield mdl-js-textfield --margin-right-20px"> \
                           <input class="mdl-textfield__input mdl-color--white" type="text" id="sample1" name="infos['+infoName+']" value="'+infoValue+'"> \
                            <label class="mdl-textfield__label info__label" for="sample1">Saisir '+infoName+' de l\'usager ici</label> \
                       </div> \
                    <button class="mdl-button mdl-js-button mdl-button--icon mdl-button--colored '+applicationFormUserInfosRemoveButtonClass+'" type="button"> \
                        <i class="material-icons">remove_circle</i> \
                    </button>';

  var otherDiv = document.getElementById("other-div");
  otherDiv.parentNode.insertBefore(newNode, otherDiv);
  componentHandler.upgradeElements(newNode);
  var select = document.getElementById(applicationFormUserInfosTypesSelectId);
  if (infoName !== "Autre") {
    for (var i = 0; i < select.options.length; i++) {
      if (select.options[i].value === infoName) {
        select.removeChild(select.options[i]);
      }
    }
  }
  select.value = "";
  document.getElementById("add-infos__ok-button").classList.add("invisible");

  querySelectorAllForEach(
    "." + applicationFormUserInfosRemoveButtonClass,
    function (element) {
      // Avoid having many times the same event handler with .onclick
      element.onclick = function(event) {
        removeInfo(element);
      };
    }
  );
}

function addInfo() {
  var inputInfoName = document.getElementById("other-type");
  var infoName = inputInfoName.value;
  inputInfoName.value = "";
  inputInfoName.parentNode.classList.remove("is-dirty");
  inputInfoName.parentNode.classList.add("invisible");
  var valueInput = document.getElementById("other-type-value");
  var infoValue = valueInput.value;
  valueInput.value = "";
  valueInput.parentNode.classList.remove("is-dirty");
  valueInput.parentNode.classList.add("invisible");
  if (infoName === "" || infoValue === "") { return; }
  addOptionalInfoRow(infoName, infoValue);
}


function setupApplicationForm() {
  querySelectorAllForEach(
    "." + applicationFormGroupCheckboxClass,
    function (checkbox) {
      checkbox.addEventListener('click', function(event) {
        showHideUsers(event.target);
      });
    }
  );

  var checkboxMandat = document.getElementById("checkbox-mandat");

  if (checkboxMandat) {
    checkboxMandat.addEventListener('click', function() {
      if(checkboxMandat.checked) {
        document.querySelector("#review-validation").disabled = false;
      } else {
        document.querySelector("#review-validation").disabled = true;
      }
    });
  }

  var userInfosTypesSelect = document.getElementById(applicationFormUserInfosTypesSelectId);
  if (userInfosTypesSelect) {
    newTypeSelected();
    userInfosTypesSelect.addEventListener('change', function() {
      newTypeSelected();
    });
  }

  var createApplicationForm = document.getElementById(createApplicationFormId);
  if (createApplicationForm) {
    createApplicationForm.addEventListener('submit', function() {
      addInfo();
    });
  }

  querySelectorAllForEach(
    "." + applicationFormUserInfosInputClass,
    function (element) {
      element.addEventListener('keydown', function(event) {
        if (event.keyCode === 13) {
          addInfo();
          return false;
        }
      });
    }
  );

  querySelectorAllForEach(
    "." + applicationFormUserInfosAddButtonClass,
    function (element) {
      element.addEventListener('click', function(event) {
        addInfo();
      });
    }
  );
}

setupApplicationForm();

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
  new SlimSelect({ select: select, selectByGroup: true, closeOnSelect: false})
});
