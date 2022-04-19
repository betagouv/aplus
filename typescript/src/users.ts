import { Tabulator, TabulatorFull } from 'tabulator-tables';
import "tabulator-tables/dist/css/tabulator.css";
import { debounceAsync } from './helpers';

const usersTableId = "tabulator-users-table";
const groupsTableId = "tabulator-groups-table";
const currentAreaValueId = "current-area-value";
const currentAreaDatasetKey = "areaId";
const searchBoxId = "searchBox";

let selectedAreaId: string | null = null;
let usersTable: Tabulator | null = null;
let groupsTable: Tabulator | null = null;

interface UserInfosGroup {
  id: string;
  name: string;
}

interface UserInfos {
  id: string;
  firstName: string | null;
  lastName: string | null;
  name: string;
  completeName: string;
  qualite: string;
  email: string;
  phoneNumber: string | null;
  helper: boolean;
  instructor: boolean;
  areas: Array<string>;
  groupNames: Array<String>;
  groups: Array<UserInfosGroup>;
  groupEmails: Array<String>;
  groupAdmin: boolean;
  admin: boolean;
  expert: boolean;
  disabled: boolean;
  sharedAccount: boolean;
  cgu: boolean;
}

interface UserGroupInfos {
  id: string;
  name: string;
  description: string | null;
  creationDate: string;
  areas: Array<string>;
  organisation: string | null,
  email: string | null;
  publicNote: string | null;
}

interface SearchResult {
  users: UserInfos[];
  groups: UserGroupInfos[];
}


const debouncedFetch = debounceAsync(fetch, 500);

async function callSearch(searchString: string): Promise<SearchResult> {
  let url: string = jsRoutes.controllers.UserController.search().url +
    "?q=" + encodeURIComponent(searchString);
  if (selectedAreaId != null) {
    url = url + "&areaId=" + selectedAreaId;
  }
  return await debouncedFetch(url).then((response) => response.json());
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
    const groups = <Array<UserInfosGroup>>cell.getRow().getData().groups;
    let links = "";
    let isNotFirst = false;
    groups.forEach((group) => {
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

  const groupNameFormatter: Tabulator.Formatter = function(cell) {
    const group = <UserGroupInfos>cell.getRow().getData();
    const groupUrl = jsRoutes.controllers.GroupController.editGroup(group.id).url;
    const html = "<a href=\"" + groupUrl + "\" target=\"_blank\" >" + group.name + "</a>";
    return html;
  };

  const rowFormatter = function(row: Tabulator.RowComponent) {
    let element = row.getElement(),
      data = row.getData();
    if (data.disabled) {
      element.classList.add("row--disabled");
    }
  };

  const usersColumns: Array<Tabulator.ColumnDefinition> = [
    { title: "", formatter: editIcon, width: 40, frozen: true },
    {
      title: "Email",
      field: "email",
      headerFilter: "input",
      width: 250,
    },
    {
      title: "Groupes",
      field: "groupNames",
      formatter: groupsFormatter,
      headerFilter: "input",
      width: 400,
    },
    {
      title: "Nom Complet",
      field: "completeName",
      headerFilter: "input",
      formatter: "html",
      width: 150,
    },
    {
      title: "BALs",
      field: "groupEmails",
      headerFilter: "input",
      width: 200,
    },
    {
      title: "Qualité",
      field: "qualite",
      headerFilter: "input",
      width: 150,
    },
    {
      title: "Téléphone",
      field: "phoneNumber",
      headerFilter: "input",
      width: 120,
    },
    {
      title: "Aidant",
      field: "helper",
      formatter: "tickCross",
      headerFilter: "tickCross",
      headerFilterParams: { tristate: true },
      headerVertical: verticalHeader,
      bottomCalc: "count",
      width: 80,
    },
    {
      title: "Instructeur",
      field: "instructor",
      formatter: "tickCross",
      headerFilter: "tickCross",
      headerFilterParams: { tristate: true },
      headerVertical: verticalHeader,
      bottomCalc: "count",
      width: 80,
    },
    {
      title: "Responsable",
      field: "groupAdmin",
      formatter: "tickCross",
      headerFilter: "tickCross",
      headerFilterParams: { tristate: true },
      headerVertical: verticalHeader,
      bottomCalc: "count",
      width: 80,
    },
    {
      title: "Partagé",
      field: "sharedAccount",
      formatter: "tickCross",
      headerFilter: "tickCross",
      headerFilterParams: { tristate: true },
      headerVertical: verticalHeader,
      bottomCalc: "count",
      width: 80,
    },
    {
      title: "Expert",
      field: "expert",
      formatter: "tickCross",
      headerFilter: "tickCross",
      headerFilterParams: { tristate: true },
      headerVertical: verticalHeader,
      bottomCalc: "count",
      width: 80,
    },
    {
      title: "Admin",
      field: "admin",
      formatter: "tickCross",
      headerFilter: "tickCross",
      headerFilterParams: { tristate: true },
      headerVertical: verticalHeader,
      bottomCalc: "count",
      width: 80,
    },
    {
      title: "Désactivé",
      field: "disabled",
      formatter: "tickCross",
      headerFilter: "tickCross",
      headerFilterParams: { tristate: true },
      headerVertical: verticalHeader,
      bottomCalc: "count",
      width: 80,
    },
    {
      title: "CGU",
      field: "cgu",
      formatter: "tickCross",
      headerFilter: "tickCross",
      headerFilterParams: { tristate: true },
      headerVertical: verticalHeader,
      bottomCalc: "count",
      width: 80,
    },
    {
      title: "Départements",
      field: "areas",
      headerFilter: "input",
      width: 200,
    },
    {
      title: "Nom et Prénom",
      field: "name",
      headerFilter: "input",
      formatter: "html",
      width: 200,
    },
    {
      title: "Nom",
      field: "lastName",
      headerFilter: "input",
      formatter: "html",
      width: 200,
    },
    {
      title: "Prénom",
      field: "firstName",
      headerFilter: "input",
      formatter: "html",
      width: 200,
    },
  ];


  const groupsColumns: Array<Tabulator.ColumnDefinition> = [
    {
      title: "Nom",
      field: "name",
      headerFilter: "input",
      formatter: groupNameFormatter,
      width: 300,
    },
    {
      title: "Organisme",
      field: "organisation",
      headerFilter: "input",
      width: 150,
    },
    {
      title: "BAL",
      field: "email",
      headerFilter: "input",
      width: 300,
    },
    {
      title: "Départements",
      field: "areas",
      headerFilter: "input",
      width: 200,
    },
    {
      title: "Description",
      field: "description",
      headerFilter: "input",
      width: 300,
    },
    {
      title: "Description détaillée",
      field: "publicNote",
      headerFilter: "input",
      width: 500,
    },
  ];



  const areaValueField = <HTMLElement | null>document.getElementById(currentAreaValueId);
  if (areaValueField != null) {
    const areaId = areaValueField.dataset[currentAreaDatasetKey];
    if (areaId) {
      selectedAreaId = areaId;
    }
  }

  const usersOptions: Tabulator.Options = {
    height: "48vh",
    rowFormatter,
    langs: {
      "fr-fr": {
        headerFilters: {
          "default": "filtrer..."
        }
      }
    },
    columns: usersColumns,
  };
  usersTable = new TabulatorFull("#" + usersTableId, usersOptions);
  usersTable.on("tableBuilt", function() {
    usersTable?.setLocale("fr-fr");
    usersTable?.setSort("name", "asc");
  });

  const groupsOptions: Tabulator.Options = {
    height: "25vh",
    rowFormatter,
    langs: {
      "fr-fr": {
        headerFilters: {
          "default": "filtrer..."
        }
      }
    },
    columns: groupsColumns,
  };
  groupsTable = new TabulatorFull("#" + groupsTableId, groupsOptions);
  groupsTable.on("tableBuilt", function() {
    groupsTable?.setLocale("fr-fr");
    groupsTable?.setSort("name", "asc");
  });



  const searchBox = <HTMLInputElement | null>document.getElementById(searchBoxId);
  if (searchBox) {
    const fillData = () => {
      const searchString = searchBox.value;
      callSearch(searchString).then((data) => {
        usersTable?.setData(data.users);
        groupsTable?.setData(data.groups);
      });
    };
    searchBox.addEventListener("input", fillData);

    fillData();
  }

}
