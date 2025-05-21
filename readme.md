# CRBC-PDF-Regs

**CRBC-PDF-Regs** is a utility script originally developed for internal processing of PDF regulations for the Consolidated Regulations of British Columbia system. It automates syncing, organizing, and ID tagging of PDF files, and builds a lookup table for downstream systems. 

## Overview

At a high level, the tool performs the following steps:

1. Reads a build configuration file provided as an argument.
2. Syncs regulation files between directories.
3. Renames numbered directories to use regulation titles.
4. Processes PDFs to ensure each has a unique ID (adding one if missing).
5. Updates or builds a lookup table.
6. Triggers a batch process to build content.

## Usage

Run the script by providing the path to the build configuration file:

```bash
your-script.bat path\to\buildConfig.txt
