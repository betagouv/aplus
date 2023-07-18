import { ColumnDefinition, Formatter, TabulatorFull } from 'tabulator-tables';
import "tabulator-tables/dist/css/tabulator.css";


// Tabulator uses the global XLSX
// https://github.com/olifolkerd/tabulator/blob/4.9.3/src/js/modules/download.js#L331
// The dependency is served in another <script>




//
// Admin page for France Service deployment
//
const franceServiceDeploymentTableTagId = "aplus-admin-deployment-france-service-table";
const franceServiceDeploymentTotalTagId = "aplus-admin-deployment-france-service-total";
const franceServiceDeploymentDownloadBtnCsvId = "aplus-admin-deployment-france-service-download-btn-csv";
const franceServiceDeploymentDownloadBtnXlsxId = "aplus-admin-deployment-france-service-download-btn-xlsx";


// TODO: macro that will generate field names from the scala case class (Scala => TS)
const franceServiceDeploymentColumns: Array<ColumnDefinition> = [
  { title: "Nom", field: "nomFranceService", sorter: "string", width: 200 },
  {
    title: "Département",
    field: "departementName",
    sorter: "string",
    formatter: (cell) => {
      const value = cell.getValue();
      const departementIsDone = cell.getRow().getData().departementIsDone;
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
    formatter: (cell) => {
      const value = cell.getValue();
      const departementIsDone = cell.getRow().getData().departementIsDone;
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
    formatter: (cell) => {
      const value = cell.getValue();
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
  { title: "Commune", field: "commune", sorter: "string" },
  { title: "Mail", field: "contactMail", sorter: "string" },
  { title: "Téléphone", field: "phone", sorter: "string" },
  { title: "Groupe", field: "matchedGroup", sorter: "string" },
]

if (window.document.getElementById(franceServiceDeploymentTableTagId)) {
  const franceServiceDeploymentTable = new TabulatorFull("#" + franceServiceDeploymentTableTagId, {
    ajaxURL: jsRoutes.controllers.ApiController.franceServiceDeployment().url,
    ajaxResponse: function(url, params, response) {
      // http://tabulator.info/docs/4.5/callbacks#ajax
      let nrOfDeployed = 0;
      let totalNr = response.length;
      for (let i = 0; i < totalNr; i++) {
        if (response[i].groupSize >= 1) {
          nrOfDeployed++;
        }
      }
      const element = window.document.getElementById(franceServiceDeploymentTotalTagId);
      if (element) {
        element.textContent = "Nombre de déploiements (plus de 1 utilisateur): " +
          nrOfDeployed + " ; Nombre total: " + totalNr
      }
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



  const csvBtn = window.document.getElementById(franceServiceDeploymentDownloadBtnCsvId);
  if (csvBtn) {
    csvBtn.onclick = () => {
      const date = new Date().toISOString().replace(/:/g, '-');
      franceServiceDeploymentTable.download(
        'csv',
        'aplus-export-deploiement-france-service-' + date + '.csv'
      );
    };
  }

  const xlsxBtn = window.document.getElementById(franceServiceDeploymentDownloadBtnXlsxId);
  if (xlsxBtn) {
    xlsxBtn.onclick = () => {
      const date = new Date().toISOString().replace(/:/g, '-');
      franceServiceDeploymentTable.download(
        "xlsx",
        'aplus-export-deploiement-france-service-' + date + '.xlsx',
        { sheetName: "Déploiement France Service" }
      );
    };
  }

}






//
// Admin page for deployment
//
const deploymentTableTagId = "aplus-admin-deployment-table";
const deploymentDownloadBtnCsvId = "aplus-admin-deployment-download-btn-csv";
const deploymentDownloadBtnXlsxId = "aplus-admin-deployment-download-btn-xlsx";


interface Organisation {
  id: string;
  shortName: string;
  name: string;
}

interface OrganisationSet {
  id: string;
  organisations: Array<Organisation>;
}

interface AreaData {
  areaId: string;
  areaName: string;
  numOfInstructorByOrganisationSet: { [index: string]: number };
  numOfOrganisationSetWithOneInstructor: number;
}

interface DeploymentData {
  organisationSets: Array<OrganisationSet>;
  areasData: Array<AreaData>;
  numOfAreasWithOneInstructorByOrganisationSet: { [index: string]: number };
}




if (window.document.getElementById(deploymentTableTagId)) {
  let url: string = jsRoutes.controllers.ApiController.deploymentData().url;
  if (window.location.search.indexOf("uniquement-fs=non") > -1) {
    url = url + "?uniquement-fs=non";
  }
  fetch(url).then((response) => response.json()).then((deploymentData: DeploymentData) => {

    const organisationFormatter: Formatter = (cell) => {
      const value = cell.getValue();
      const areaName = cell.getRow().getData().areaName;

      if (areaName === "Couverture") {
        if (value < 1) {
          cell.getElement().classList.add("mdl-color--red");
        } else if (value <= 4) {
          cell.getElement().classList.add("mdl-color--light-green");
        } else if (value <= 7) {
          cell.getElement().classList.add("mdl-color--medium-green");
        } else {
          cell.getElement().classList.add("mdl-color--dark-green");
        }
        return value + ' / ' + deploymentData.areasData.length;
      }
      if (areaName !== "Avancement") {
        if (value < 1) {
          cell.getElement().classList.add("mdl-color--red");
        } else if (value >= 2) {
          cell.getElement().classList.add("mdl-color--green");
        } else {
          cell.getElement().classList.add("mdl-color--yellow");
        }
      }

      return value;
    };

    const groupingColumns = deploymentData.organisationSets.map((orgSet) => {
      const title = orgSet.organisations.map((organisation) => organisation.shortName).join(" / ");
      const field = orgSet.id;

      const column: ColumnDefinition = {
        title,
        field,
        sorter: "number",
        headerVertical: true,
        formatter: organisationFormatter,
      };
      return column;
    });

    const areaColumn: ColumnDefinition =
    {
      title: "Département", field: "areaName", sorter: "string", width: 150,
      frozen: true
    };
    const totalColumn: ColumnDefinition =
    {
      title: "Couverture",
      field: "total",
      sorter: "number",
      headerVertical: true,
      formatter: (cell) => {
        let value = cell.getValue();
        if (value !== '') {
          if (value < 1) {
            cell.getElement().classList.add("mdl-color--red");
          } else if (value <= 4) {
            cell.getElement().classList.add("mdl-color--light-green");
          } else if (value <= 7) {
            cell.getElement().classList.add("mdl-color--medium-green");
          } else {
            cell.getElement().classList.add("mdl-color--dark-green");
          }
          value = value + ' / ' + deploymentData.organisationSets.length;
        }
        return value;
      }
    };


    const columns = [areaColumn].concat(groupingColumns).concat([totalColumn]);

    type Row = { [index: string]: string | number };
    const data = deploymentData.areasData.map((areaData) => {
      let rowData: Row = {
        areaName: areaData.areaName,
        total: areaData.numOfOrganisationSetWithOneInstructor,
        ...areaData.numOfInstructorByOrganisationSet
      };
      return rowData;
    });
    const lastRow: Row = {
      areaName: "Couverture",
      total: "",
      ...deploymentData.numOfAreasWithOneInstructorByOrganisationSet
    };
    data.push(lastRow);

    const table = new TabulatorFull("#" + deploymentTableTagId, {
      data,
      layout: "fitColumns",
      responsiveLayout: "hide",
      movableColumns: true,
      resizableRows: true,
      initialSort: [],
      columns,
      height: "85vh",
    });

    const csvBtn = window.document.getElementById(deploymentDownloadBtnCsvId);
    if (csvBtn) {
      csvBtn.onclick = () => {
        const date = new Date().toISOString().replace(/:/g, '-');
        table.download('csv', 'aplus-export-deploiement-' + date + '.csv');
      };
    }

    const xlsxBtn = window.document.getElementById(deploymentDownloadBtnXlsxId);
    if (xlsxBtn) {
      xlsxBtn.onclick = () => {
        const date = new Date().toISOString().replace(/:/g, '-');
        table.download("xlsx", 'aplus-export-deploiement-' + date + '.xlsx', { sheetName: "Déploiement" });
      };
    }

  });
}
