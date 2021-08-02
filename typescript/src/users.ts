// Important Note:
// import * as Tabulator from 'tabulator-tables'
// fails with error
// TS2306: File '.../node_modules/@types/tabulator-tables/index.d.ts' is not a module.
// We have to do a bit of contrivances to make it work.
// In global.d.ts, there is
// declare module "tabulator-tables" { export default Tabulator; }
// To be noted that we can make it work like this, however, without TS types
// import 'tabulator-tables';
// const Tabulator = require('tabulator-tables').default;
import Tabulator from 'tabulator-tables'
import "tabulator-tables/dist/css/tabulator.css";


const usersTableId = "tabulator-users-table"
const currentAreaValueId = "current-area-value"
const currentAreaDatasetKey = "areaId"


interface UserInfosGroup {
  id: string;
  name: string;
}



if (window.document.getElementById(usersTableId)) {
  const verticalHeader = false;
  const editIcon: Tabulator.Formatter = function(cell) {
    //plain text value
    let uuid = cell.getRow().getData().id;
    let url = jsRoutes.controllers.UserController.editUser(uuid).url;
    return "<a href='" + url + "' target=\"_blank\" ><i class='fas fa-user-edit'></i></a>";
  };

  const groupsFormatter: Tabulator.Formatter = function(cell) {
    let groups = <Array<UserInfosGroup>>cell.getRow().getData().groups;
    let links = "";
    let isNotFirst = false;
    groups.forEach(group => {
      let groupUrl = jsRoutes.controllers.GroupController.editGroup(group.id).url;
      let groupName = group.name;
      if (isNotFirst) {
        links += ", ";
      }
      links += "<a href=\"" + groupUrl + "\" target=\"_blank\" >" + groupName + "</a>";
      isNotFirst = true;
    });
    return links;
  };

  const rowFormatter = function(row: Tabulator.RowComponent) {
    let element = row.getElement(),
      data = row.getData();
    if (data.disabled) {
      element.classList.add("row--disabled");
    }
  };

  const columns: Array<Tabulator.ColumnDefinition> = [
    { title: "", formatter: editIcon, width: 40, frozen: true },
    { title: "Nom et Prénom", field: "name", headerFilter: "input", formatter: "html" },
    { title: "Nom", field: "lastName", headerFilter: "input", frozen: true, formatter: "html" },
    { title: "Prénom", field: "firstName", headerFilter: "input", frozen: true, formatter: "html" },
    { title: "Groupes", field: "groupNames", formatter: groupsFormatter, headerFilter: "input" },
    { title: "Email", field: "email", headerFilter: "input" },
    { title: "Qualité", field: "qualite", headerFilter: "input" },
    { title: "Téléphone", field: "phoneNumber", headerFilter: "input" },
    {
      title: "Droits",
      columns: [
        {
          title: "Aidant",
          field: "helper",
          formatter: "tickCross",
          headerFilter: "tickCross",
          headerFilterParams: { tristate: true },
          headerVertical: verticalHeader,
          bottomCalc: "count"
        },
        {
          title: "Instructeur",
          field: "instructor",
          formatter: "tickCross",
          headerFilter: "tickCross",
          headerFilterParams: { tristate: true },
          headerVertical: verticalHeader,
          bottomCalc: "count"
        },
        {
          title: "Responsable",
          field: "groupAdmin",
          formatter: "tickCross",
          headerFilter: "tickCross",
          headerFilterParams: { tristate: true },
          headerVertical: verticalHeader,
          bottomCalc: "count"
        },
        {
          title: "Partagé",
          field: "sharedAccount",
          formatter: "tickCross",
          headerFilter: "tickCross",
          headerFilterParams: { tristate: true },
          headerVertical: verticalHeader,
          bottomCalc: "count"
        },
        {
          title: "Expert",
          field: "expert",
          formatter: "tickCross",
          headerFilter: "tickCross",
          headerFilterParams: { tristate: true },
          headerVertical: verticalHeader,
          bottomCalc: "count"
        },
        {
          title: "Admin",
          field: "admin",
          formatter: "tickCross",
          headerFilter: "tickCross",
          headerFilterParams: { tristate: true },
          headerVertical: verticalHeader,
          bottomCalc: "count"
        },
        {
          title: "Désactivé",
          field: "disabled",
          formatter: "tickCross",
          headerFilter: "tickCross",
          headerFilterParams: { tristate: true },
          headerVertical: verticalHeader,
          bottomCalc: "count"
        }
      ]
    },
    {
      title: "CGU",
      field: "cgu",
      formatter: "tickCross",
      headerFilter: "tickCross",
      headerFilterParams: { tristate: true },
      headerVertical: verticalHeader,
      bottomCalc: "count"
    },
    { title: "BALs", field: "groupEmails", headerFilter: "input" },
    { title: "Départements", field: "areas", headerFilter: "input" },
  ];


  const areaValueField = <HTMLElement | null>document.getElementById(currentAreaValueId);
  const areaId: string = areaValueField.dataset[currentAreaDatasetKey];
  const options: Tabulator.Options = {
    ajaxURL: jsRoutes.controllers.UserController.allJson(areaId).url,
    height: "80vh",
    columnMaxWidth: 300,
    rowFormatter: rowFormatter,
    langs: {
      "fr-fr": {
        pagination: {
          page_size: "Taille de la page",
          first: "Premier",
          first_title: "Première Page",
          last: "Dernier",
          last_title: "Dernière Page",
          prev: "Précédent",
          prev_title: "Page Précédente",
          next: "Suivant",
          next_title: "Page Suivante"
        },
        headerFilters: {
          default: "filtrer..." //default header filter placeholder text
        }
      }
    },
    columns: columns,
  }
  const table = new Tabulator("#" + usersTableId, options);
  table.setLocale("fr-fr");
  table.setSort("name", "asc");
}
