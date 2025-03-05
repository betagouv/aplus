/* global jsRoutes */
import { ColumnDefinition, Formatter, Options, Tabulator, TabulatorFull } from 'tabulator-tables';
import "tabulator-tables/dist/css/tabulator.css";

const applicationsTableId = "tabulator-applications-table";
const applicationsAreaId = "applications-area-id";
const applicationsNumOfMonthsDisplayedId = "num-of-months-displayed-box";
const downloadBtnCsv = "applications-download-btn-csv";
const downloadBtnXlsx = "applications-download-btn-xlsx";
const queryParamAreaId = "areaId";
const queryParamNumOfMonthsDisplayed = "nombreDeMoisAffiche";



if (window.document.getElementById(applicationsTableId)) {



  let applicationsTable: Tabulator | null = null;

  const ajaxUrl: string = jsRoutes.controllers.ApplicationController.applicationsMetadata().url;



  const extractQueryParams: () => { areaId: string | null, nombreDeMoisAffiche: string } = () => {
    const params = new URL(window.location.href).searchParams;
    const areaIdOpt = params.get(queryParamAreaId);
    const numOfMonthsDisplayedOpt = params.get(queryParamNumOfMonthsDisplayed);
    return {
      areaId: areaIdOpt,
      nombreDeMoisAffiche: numOfMonthsDisplayedOpt ? numOfMonthsDisplayedOpt : "3",
    };
  };

  // Changed by event listeners
  let ajaxParams: { areaId: string | null, nombreDeMoisAffiche: string } = extractQueryParams();



  // Set the initial area id
  const areaSelect = <HTMLSelectElement | null>document.getElementById(applicationsAreaId);
  if (areaSelect) {
    areaSelect.addEventListener('change', (e) => {
      const target = e.target as HTMLSelectElement;
      const id: string = target.value;
      ajaxParams.areaId = id;
      applicationsTable?.setData(ajaxUrl);
    });
  }

  // Set number of months
  const monthsInput = <HTMLElement | null>document.getElementById(applicationsNumOfMonthsDisplayedId);
  if (monthsInput) {
    monthsInput.addEventListener('input', (e) => {
      const target = e.target as HTMLInputElement;
      const num: string = target.value;
      ajaxParams.nombreDeMoisAffiche = num;
      applicationsTable?.setData(ajaxUrl);
    });
  }



  // Download
  const downloadFilename: () => string = () => {
    let areaName: string = "";
    if (areaSelect) {
      const selected = areaSelect.options[areaSelect.selectedIndex];
      if (selected && selected.text) {
        areaName = selected.text + " - ";
      }
    }
    const date = new Date().toLocaleDateString('fr-fr', { year: "numeric", month: "numeric", day: "numeric" });
    const filename = 'Demandes Administration+ - ' + areaName + date;
    return filename;
  };

  const csvDownloadBtn = <HTMLElement | null>document.getElementById(downloadBtnCsv);
  if (csvDownloadBtn) {
    csvDownloadBtn.addEventListener('click', (_) => {
      applicationsTable?.download(
        'csv',
        downloadFilename() + '.csv',
        { delimiter: ";" }
      );
    });
  }

  const xlsxDownloadBtn = <HTMLElement | null>document.getElementById(downloadBtnXlsx);
  if (xlsxDownloadBtn) {
    xlsxDownloadBtn.addEventListener('click', (_) => {
      applicationsTable?.download(
        'xlsx',
        downloadFilename() + '.xlsx',
        { sheetName: "Demandes Administration+" }
      );
    });
  }



  // Setup Tabulator
  const linkFormatter: Formatter = (cell) => {
    let uuid = cell.getRow().getData().id;
    let authorized = cell.getRow().getData().currentUserCanSeeAnonymousApplication;
    let url = jsRoutes.controllers.ApplicationController.show(uuid).url;
    if (authorized) {
      return "<a href='" + url + "' target=\"_blank\" ><i class='fas fa-arrow-up-right-from-square'></i></a>";
    } else {
      return "";
    }
  };

  const usefulnessFormatter: Formatter = (cell) => {
    let value = cell.getValue();
    if (value) {
      if (value === "Oui") {
        cell.getElement().classList.add("mdl-color--light-green");
      } else if (value === "Non") {
        cell.getElement().classList.add("mdl-color--red");
      }
    }
    return value;
  };

  const pertinenceFormatter: Formatter = (cell) => {
    let value = cell.getValue();
    if (value && value === "Non") {
      cell.getElement().classList.add("mdl-color--red");
    }
    return value;
  };

  const columns: Array<ColumnDefinition> = [
    {
      title: "",
      field: "id",
      formatter: linkFormatter,
      hozAlign: "center",
      bottomCalc: "count",
      frozen: true
    },
    {
      title: "No",
      field: "internalId",
      headerFilter: "input",
      hozAlign: "right",
      sorter: "number",
      bottomCalc: "count",
      titleDownload: "Numéro"
    },
    {
      title: "Création",
      field: "creationDateFormatted",
      headerFilter: "input",
      download: false
    },
    {
      title: "Date de création",
      field: "creationDay",
      visible: false,
      download: true
    },
    {
      title: "Territoire",
      field: "areaName",
      headerFilter: "input",
    },
    {
      title: "Avancement",
      field: "status",
      headerFilter: "input",
    },
    {
      title: "Créateur",
      field: "creatorUserName",
      headerFilter: "input",
      maxWidth: 300,
    },
    {
      title: "Structure mandatée",
      field: "groups.creatorGroupName",
      headerFilter: "input",
      maxWidth: 300,
    },
    {
      title: "Réseau",
      field: "network",
      headerFilter: "input",
      maxWidth: 90,
    },
    {
      title: "Invités",
      field: "stats.numberOfInvitedUsers",
      hozAlign: "right",
      headerFilter: "input",
      sorter: "number"
    },
    {
      title: "Messages",
      field: "stats.numberOfMessages",
      hozAlign: "right",
      headerFilter: "input",
      sorter: "number"
    },
    {
      title: "Réponses",
      field: "stats.numberOfAnswers",
      hozAlign: "right",
      headerFilter: "input",
      sorter: "number"
    },
    {
      title: "Utile",
      field: "usefulness",
      hozAlign: "center",
      formatter: usefulnessFormatter,
      headerFilter: "input",
    },
    {
      title: "Pertinente",
      field: "pertinence",
      hozAlign: "center",
      formatter: pertinenceFormatter,
      headerFilter: "input",
    },
    {
      title: "Clôture",
      field: "closedDateFormatted",
      headerFilter: "input",
      download: false
    },
    {
      title: "Date de clôture",
      field: "closedDay",
      visible: false,
      download: true
    },
    {
      title: "Groupes du demandeur",
      field: "groups.creatorUserGroupsNames",
      headerFilter: "input",
      maxWidth: 300,
      visible: false,
      download: true
    },
    {
      title: "Groupes sollicités",
      field: "groups.groupNamesInvitedAtCreation",
      headerFilter: "input",
      maxWidth: 300,
    },
    {
      title: "Groupes invités",
      field: "groups.groupNamesInvitedOnAnswers",
      headerFilter: "input",
      maxWidth: 300,
    },
    {
      title: "Délais de première réponse (Jours)",
      field: "stats.firstAnswerTimeInDays",
      hozAlign: "right",
      headerFilter: "input",
      sorter: "number",
      bottomCalc: "avg",
    },
    {
      title: "Délais de clôture (Jours)",
      field: "stats.resolutionTimeInDays",
      hozAlign: "right",
      headerFilter: "input",
      sorter: "number",
      bottomCalc: "avg",
    },
    {
      title: "Délais de première réponse (heures)",
      field: "stats.firstAnswerTimeInHours",
      hozAlign: "right",
      headerFilter: "input",
      sorter: "number",
      bottomCalc: "avg",
      visible: false,
      download: true
    },
    {
      title: "Délais de clôture (heures)",
      field: "stats.resolutionTimeInHours",
      hozAlign: "right",
      headerFilter: "input",
      sorter: "number",
      bottomCalc: "avg",
      visible: false,
      download: true
    },
  ];

  const options: Options = {
    height: "75vh",
    langs: {
      "fr-fr": {
        "data": {
          "loading": "Chargement",
          "error": "Erreur",
        },
        "headerFilters": {
          "default": "filtrer..."
        }
      }
    },
    columns,
    ajaxURL: ajaxUrl,
    ajaxParams: () => ajaxParams,
    ajaxResponse(_url, _params, response) {
      return response.applications;
    }
  };
  applicationsTable = new TabulatorFull("#" + applicationsTableId, options);
  applicationsTable.on("tableBuilt", function() {
    applicationsTable?.setLocale("fr-fr");
    // Weird behevior: setSort throws TypeError if applied now
    setTimeout(() => applicationsTable?.setSort("internalId", "desc"), 2000);
  });

}
