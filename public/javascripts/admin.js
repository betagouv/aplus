//
// Admin page for France Service deployment
//

var franceServiceDeploymentTableTagId = "aplus-admin-deployment-france-service-table"
var franceServiceDeploymentTotalTagId = "aplus-admin-deployment-france-service-total"
var franceServiceDeploymentDownloadBtnCsvId = "aplus-admin-deployment-france-service-download-btn-csv"
var franceServiceDeploymentDownloadBtnXlsxId = "aplus-admin-deployment-france-service-download-btn-xlsx"

// TODO: macro that will generate field names from the scala case class (Scala => TS)
var franceServiceDeploymentColumns = [
  {title: "Nom", field: "nomFranceService", sorter: "string", width: 200},
  {
    title: "Département",
    field: "departementName",
    sorter: "string",
    formatter: function(cell, formatterParams, onRendered) {
      var value = cell.getValue();
      var departementIsDone = cell.getRow().getData().departementIsDone;
      if (departementIsDone) {
        cell.getElement().classList.add("mdl-color--green");
      } else {
        cell.getElement().classList.add("mdl-color--red");
      }
      return value;
    }
  },
  {
    title: "Code",
    field: "departementCode",
    sorter: "string",
    formatter: function(cell, formatterParams, onRendered) {
      var value = cell.getValue();
      var departementIsDone = cell.getRow().getData().departementIsDone;
      if (departementIsDone) {
        cell.getElement().classList.add("mdl-color--green");
      } else {
        cell.getElement().classList.add("mdl-color--red");
      }
      return value;
    }
  },
  {
    title: "Déploiement",
    field: "groupSize",
    sorter: "number",
    formatter: function(cell, formatterParams, onRendered) {
      var value = cell.getValue();
      if (value < 1) {
        cell.getElement().classList.add("mdl-color--red");
      } else if (value == 1) {
        cell.getElement().classList.add("mdl-color--yellow");
      } else {
        cell.getElement().classList.add("mdl-color--green");
      }
      return value;
    }
  },
  {title: "Commune", field: "commune", sorter: "string"},
  {title: "Mail", field: "contactMail", sorter: "string"},
  {title: "Téléphone", field: "phone", sorter: "string"},
  {title: "Groupe", field: "matchedGroup", sorter: "string"},
]

if (window.document.getElementById(franceServiceDeploymentTableTagId)) {
  var franceServiceDeploymentTable = new Tabulator("#" + franceServiceDeploymentTableTagId, {
    ajaxURL: jsRoutes.controllers.ApiController.franceServiceDeployment().url,
    ajaxResponse: function(url, params, response) {
      // http://tabulator.info/docs/4.5/callbacks#ajax
      var nrOfDeployed = 0;
      var totalNr = response.length;
      for (var i = 0; i < totalNr; i++) {
        if (response[i].groupSize >= 1) {
          nrOfDeployed++;
        }
      }
      var element = window.document.getElementById(franceServiceDeploymentTotalTagId);
      element.textContent = "Nombre de déploiements (plus de 1 utilisateur): " +
        nrOfDeployed + " ; Nombre total: " + totalNr
      return response; //return the response data to tabulator
    },
    layout: "fitColumns",      //fit columns to width of table
    responsiveLayout: "hide",  //hide columns that dont fit on the table
    movableColumns: true,      //allow column order to be changed
    resizableRows: true,       //allow row order to be changed
    initialSort: [],
    columns: franceServiceDeploymentColumns,
    height: "75vh",
  });

  function downloadCSV() {
    var date = new Date().toISOString().replace(/:/g,'-');
    franceServiceDeploymentTable.download(
      'csv',
      'aplus-export-deploiement-france-service-'+date+'.csv'
    );
  }

  function downloadXLSX() {
    var date = new Date().toISOString().replace(/:/g,'-');
    franceServiceDeploymentTable.download(
      "xlsx",
      'aplus-export-deploiement-france-service-'+date+'.xlsx',
      {sheetName:"Déploiement France Service"}
    );
  }

  window.document.getElementById(franceServiceDeploymentDownloadBtnCsvId).onclick = downloadCSV;
  window.document.getElementById(franceServiceDeploymentDownloadBtnXlsxId).onclick = downloadXLSX;

}
