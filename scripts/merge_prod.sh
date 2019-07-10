#!/bin/sh
git checkout master; git pull; git checkout prod; git merge --ff master; git push origin prod; git checkout master
