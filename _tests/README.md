Testing the GDI Workflow
========================

This document describes how to test the GDI workflow on the local deployment.
It provides the roles and steps to execute as well as the scripts to automate
the steps (**work in progress**).


Prerequisites
-------------

Besides having the local deployment up and running, here are some additional
requirements to be resolved before any testing can be performed.

1. **Users** – defined in the mock-OIDC solution:
   1. `researcher.approved` – the user who will request access to the data;
   2. `researcher.denied` – the user who will request access to the data;
   3. `dataset.admin` – the user who manages the organisation having the data;
   4. `dataset.uploader` – the user who uploads data to SDA and Beacon;
   5. `dataset.dac` – the user (DAC) who reviews data-access application.
   6. Use `Test1ng` as the password for each user to keep it simple.
2. **REMS** organisation:
   1. `GDI-EE` as the name of the organisation;
   2. add `dataset.admin`, `dataset.uploader` and `dataset.dac` as member-users

Suggested scripts:

* `add_config_oidc`
* `add_config_rems`



GDI Workflow
------------

The script for executing the full workflow: `all_steps [path_to_vcf]`.


### 1. Adding a new dataset

1. Upload a VCF file to SDA (the `sda-inbox` bucket)
2. Verify that the uploaded file is visible in the MinIO console:
   * note: data ingestion from inbox to archive takes some time
   * the is finally under the `sda-archive` bucket
   * the file is renamed to some UUID value
   * the file is encrypted (crypt4gh)
2. Upload the same VCF file to Beacon and trigger its import (beacon-tools).
   * use the Beacon API to check that the number of datasets has increased
3. Register the dataset in REMS.
   * the dataset needs to become visible for applying access.

Suggested scripts:

* `add_dataset [path_to_vcf]`


### 2. Discovering the dataset

1. Open the Beacon web page (contains the search query form).
2. Enter following criteria:
   * TODO
3. Verify that the dataset is found.
4. Change the search criteria:
   * TODO
5. Verify that the dataset is NOT found.

Suggested scripts:

* `beacon_search [path_to_query.json]`


### 3. Requesting access

1. Open the REMS web page.
2. Log in as `researcher.approved`.
   1. Find the dataset, fill in and submit the application.
   2. Log out.
2. Log in as `researcher.denied`.
   1. Find the dataset, fill in and submit the application.
   2. Log out.
3. Log in as `dataset.dac`.
   1. Find the application of `researcher.approved` and approve it.
   2. Find the application of `researcher.denied` and reject it.
   3. Log Out
4. Log in as `researcher.approved`.
   1. Verify that you have access to the dataset according to REMS.
   2. Log Out
5. Log in as `researcher.denied`.
  1. Verify that you don't have access to the dataset according to REMS.
  2. Log Out

Suggested scripts:

* `request_access_request [user] [dataset_name]`
* `request_access_approve [dataset_name] [yes/no]`
* `request_access_verify [user] [dataset_name]`


### 4. Accessing the dataset

1. Open the SDA authentication page.
2. Log in as `researcher.approved`.
3. Download the dataset file.
   1. Using `s3cmd`
   2. Using `htsget`
   3. Verify that file is decrypted and valid (compared to original VCF)
4. Open the SDA authentication page.
5. Log in as `researcher.denied`.
6. Fail to download the dataset file.
   1. Using `s3cmd`
   2. Using `htsget`


Suggested scripts:

* `request_data [s3cmd/htsget] [user] [dataset_ref] [file_ref]`
