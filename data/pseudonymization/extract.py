import pandas as pd
import re


names_df = pd.read_csv("nat2021_csv.zip", sep=";", compression="zip")

def capitalize(s):
    return re.sub(r"(^|\W)(\w)", lambda a: a.group(1) + a.group(2).upper(), s.lower())


names_df["naissance"] = pd.to_numeric(names_df["annais"], errors="coerce", downcast="integer")
names_df["prenom"] = [capitalize(str(s)) for s in names_df["preusuel"]]

usage_series = names_df[(names_df["naissance"] < 1950) & (names_df["prenom"] != "_prenoms_rares")].groupby(["sexe","prenom"])["nombre"].sum()


def top(series, n):
    return series.sort_values(ascending=False)[:n]


prenoms_sort = usage_series.groupby("sexe", group_keys=False).apply(top, n=1000)

prenoms_sort.to_csv("common_first_names.csv")
