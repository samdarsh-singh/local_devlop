CREATE TYPE scope AS ENUM ('private', 'public');
--;;
CREATE TYPE itemtype AS ENUM ('text','texta','label','license','attachment','referee','checkbox','dropdown','date');
--;;
CREATE TYPE license_status AS ENUM ('approved','rejected');
--;;
CREATE TYPE license_state AS ENUM ('created','approved','rejected');
--;;
CREATE TYPE application_event_type AS ENUM (
  'apply',   -- draft or returned --> applied
  'approve', -- applied --> applied or approved
  'autoapprove', -- like approve but when there are no approvers for the round
  'reject',  -- applied --> rejected
  'return',   -- applied --> returned
  'review',   -- applied --> applied or approved
  'review-request', -- applied --> applied
  'withdraw',   -- applied --> withdrawn
  'close',   -- any --> closed
  'third-party-review' -- applied --> applied
);
--;;
CREATE TYPE application_state AS ENUM ('applied','approved','rejected','returned','withdrawn','closed','draft');
--;;
CREATE TYPE item_state AS ENUM ('disabled','enabled','copied');
--;;
CREATE TYPE license_type AS ENUM ('text','attachment','link');
--;;
CREATE TYPE workflow_actor_role AS ENUM ('approver','reviewer');
--;;
CREATE TABLE resource (
  id serial NOT NULL PRIMARY KEY,
  modifierUserId varchar(255) NOT NULL,
  prefix varchar(255) NOT NULL,
  resId varchar(255) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL
);
--;;
CREATE TABLE workflow (
  id serial NOT NULL PRIMARY KEY,
  ownerUserId varchar(255) NOT NULL,
  modifierUserId varchar(255) NOT NULL,
  title varchar(256) NOT NULL,
  fnlround integer NOT NULL,
  visibility scope NOT NULL DEFAULT 'private',
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL
);
--;;
CREATE TABLE application_form (
  id serial NOT NULL PRIMARY KEY,
  ownerUserId varchar(255) NOT NULL,
  modifierUserId varchar(255) NOT NULL,
  title varchar(256) NOT NULL, -- TODO: not localized yet, but not used either?
  visibility scope NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL
);
--;;
CREATE TABLE catalogue_item (
  id serial NOT NULL PRIMARY KEY,
  title varchar(256) NOT NULL,
  resId integer DEFAULT NULL,
  wfId integer DEFAULT NULL,
  formId integer DEFAULT '1',
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT catalogue_item_ibfk_1 FOREIGN KEY (resId) REFERENCES resource (id),
  CONSTRAINT catalogue_item_ibfk_2 FOREIGN KEY (wfId) REFERENCES workflow (id),
  CONSTRAINT catalogue_item_ibfk_3 FOREIGN KEY (formId) REFERENCES application_form (id)
);
--;;
CREATE TABLE catalogue_item_application (
  id serial NOT NULL PRIMARY KEY,
  applicantUserId varchar(255) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  modifierUserId varchar(255) DEFAULT NULL,
  wfid integer DEFAULT NULL,
  CONSTRAINT catalogue_item_application_ibfk_1 FOREIGN KEY (wfid) REFERENCES workflow (id)
);
--;;
CREATE TABLE application_form_item (
  id serial NOT NULL PRIMARY KEY,
  ownerUserId varchar(255) NOT NULL,
  modifierUserId varchar(255) NOT NULL,
  type itemtype DEFAULT NULL,
  value bigint NOT NULL,
  visibility scope NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL
);
--;;
CREATE TABLE application_form_item_localization (
  itemId integer NOT NULL,
  langCode varchar(64), -- null means default value
  title varchar(256) NOT NULL,
  -- the old schema had this, but we don't use it currently:
  toolTip varchar(256) DEFAULT NULL,
  inputPrompt varchar(256) DEFAULT NULL,
  -- do we need ownerUserId, modifierUserId, visibility, start, end?
  UNIQUE (itemId, langCode), -- can't be PRIMARY KEY since langCode can be null
  FOREIGN KEY (itemId) REFERENCES application_form_item (id)
);
--;;
CREATE TABLE application_form_item_map (
  id serial NOT NULL PRIMARY KEY,
  formId integer DEFAULT NULL,
  formItemId integer DEFAULT NULL,
  formItemOptional boolean NOT NULL DEFAULT FALSE,
  modifierUserId varchar(255) NOT NULL,
  itemOrder integer DEFAULT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT application_form_item_map_ibfk_1 FOREIGN KEY (formId) REFERENCES application_form (id),
  CONSTRAINT application_form_item_map_ibfk_2 FOREIGN KEY (formItemId) REFERENCES application_form_item (id)
);
--;;
CREATE TABLE license (
  id serial NOT NULL PRIMARY KEY,
  ownerUserId varchar(255) NOT NULL,
  modifierUserId varchar(255) NOT NULL,
  title varchar(256) NOT NULL,
  type license_type NOT NULL,
  textContent varchar(16384) DEFAULT NULL,
  attId integer DEFAULT NULL,
  visibility scope NOT NULL DEFAULT 'private',
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL
);
--;;
CREATE TABLE application_license_approval_values (
  id serial NOT NULL PRIMARY KEY,
  catAppId integer DEFAULT NULL,
  formMapId integer DEFAULT NULL, -- this is used for form items of type `license`, we don't have those yet
  licId integer NOT NULL,
  modifierUserId varchar(255) DEFAULT NULL,
  state license_status NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT application_license_approval_values_ibfk_1 FOREIGN KEY (catAppId) REFERENCES catalogue_item_application (id),
  CONSTRAINT application_license_approval_values_ibfk_2 FOREIGN KEY (formMapId) REFERENCES application_form_item_map (id),
  CONSTRAINT application_license_approval_values_ibfk_3 FOREIGN KEY (licId) REFERENCES license (id)
);
--;;
CREATE TABLE application_text_values (
  id serial NOT NULL PRIMARY KEY,
  catAppId integer DEFAULT NULL,
  formMapId integer DEFAULT NULL,
  modifierUserId varchar(255) NOT NULL,
  value varchar(4096) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT application_text_values_ibfk_1 FOREIGN KEY (catAppId) REFERENCES catalogue_item_application (id),
  CONSTRAINT application_text_values_ibfk_2 FOREIGN KEY (formMapId) REFERENCES application_form_item_map (id),
  UNIQUE (catAppId, formMapId)
);
--;;
CREATE TABLE workflow_actors (
  id serial NOT NULL PRIMARY KEY,
  wfId integer DEFAULT NULL,
  actorUserId varchar(255) NOT NULL,
  role workflow_actor_role NOT NULL,
  round integer NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT workflow_actors_ibfk_1 FOREIGN KEY (wfId) REFERENCES workflow (id)
);
--;;
CREATE TABLE catalogue_item_application_items (
  catAppId integer DEFAULT NULL,
  catItemId integer DEFAULT NULL,
  CONSTRAINT catalogue_item_application_items_catAppId FOREIGN KEY (catAppId) REFERENCES catalogue_item_application (id),
  CONSTRAINT catalogue_item_application_items_catItemId FOREIGN KEY (catItemId) REFERENCES catalogue_item (id)
);
--;;
CREATE TABLE catalogue_item_application_free_comment_values (
  id serial NOT NULL PRIMARY KEY,
  userId varchar(255) NOT NULL,
  catAppId integer DEFAULT NULL,
  comment varchar(4096) DEFAULT NULL,
  public boolean NOT NULL DEFAULT FALSE,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT catalogue_item_application_free_comment_values_ibfk_1 FOREIGN KEY (catAppId) REFERENCES catalogue_item_application (id)
);
--;;
CREATE TABLE catalogue_item_application_licenses (
  id serial NOT NULL PRIMARY KEY,
  catAppId integer DEFAULT NULL,
  licId integer DEFAULT NULL,
  actorUserId varchar(255) NOT NULL,
  round integer NOT NULL,
  stalling boolean NOT NULL DEFAULT FALSE,
  state license_state NOT NULL DEFAULT 'created',
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT catalogue_item_application_licenses_ibfk_1 FOREIGN KEY (catAppId) REFERENCES catalogue_item_application (id),
  CONSTRAINT catalogue_item_application_licenses_ibfk_2 FOREIGN KEY (licId) REFERENCES license (id)
);
--;;
CREATE TABLE catalogue_item_application_members (
  id serial NOT NULL PRIMARY KEY,
  catAppId integer DEFAULT NULL,
  memberUserId varchar(255) NOT NULL,
  modifierUserId varchar(255) DEFAULT '-1',
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT catalogue_item_application_members_ibfk_1 FOREIGN KEY (catAppId) REFERENCES catalogue_item_application (id)
);
--;;
CREATE TABLE catalogue_item_application_metadata (
  id serial NOT NULL PRIMARY KEY,
  userId varchar(255) NOT NULL,
  catAppId integer DEFAULT NULL,
  key varchar(32) NOT NULL,
  value varchar(256) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT catalogue_item_application_metadata_ibfk_1 FOREIGN KEY (catAppId) REFERENCES catalogue_item_application (id)
);
--;;
CREATE TABLE catalogue_item_application_predecessor (
  id serial NOT NULL PRIMARY KEY,
  pre_catAppId integer DEFAULT NULL,
  suc_catAppId integer DEFAULT NULL,
  modifierUserId varchar(255) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT catalogue_item_application_predecessor_ibfk_1 FOREIGN KEY (pre_catAppId) REFERENCES catalogue_item_application (id),
  CONSTRAINT catalogue_item_application_predecessor_ibfk_2 FOREIGN KEY (suc_catAppId) REFERENCES catalogue_item_application (id)
);
--;;
CREATE TABLE catalogue_item_localization (
  id serial NOT NULL PRIMARY KEY,
  catId integer DEFAULT NULL,
  langCode varchar(64) NOT NULL,
  title varchar(256) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT catalogue_item_localization_ibfk_1 FOREIGN KEY (catId) REFERENCES catalogue_item (id)
);
--;;
CREATE TABLE catalogue_item_state (
  id serial NOT NULL PRIMARY KEY,
  catId integer DEFAULT NULL,
  modifierUserId varchar(255) NOT NULL,
  state item_state DEFAULT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT catalogue_item_state_ibfk_1 FOREIGN KEY (catId) REFERENCES catalogue_item (id)
);
--;;
CREATE TABLE entitlement (
  id serial NOT NULL PRIMARY KEY,
  resId integer DEFAULT NULL,
  catAppId integer DEFAULT NULL,
  userId varchar(255) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT entitlement_ibfk_1 FOREIGN KEY (resId) REFERENCES resource (id),
  CONSTRAINT entitlement_ibfk_2 FOREIGN KEY (catAppId) REFERENCES catalogue_item_application (id)
);
--;;
CREATE TABLE license_localization (
  id serial NOT NULL PRIMARY KEY,
  licId integer DEFAULT NULL,
  langCode varchar(64) NOT NULL,
  title varchar(256) NOT NULL,
  textContent varchar(16384) DEFAULT NULL,
  attId integer DEFAULT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT license_localization_ibfk_1 FOREIGN KEY (licId) REFERENCES license (id)
);
--;;
CREATE TABLE resource_close_period (
  id serial NOT NULL PRIMARY KEY,
  resId integer DEFAULT NULL,
  closePeriod integer DEFAULT NULL,
  modifierUserId varchar(255) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT resource_close_period_ibfk_1 FOREIGN KEY (resId) REFERENCES resource (id)
);
--;;
CREATE TABLE resource_licenses (
  id serial NOT NULL PRIMARY KEY,
  resId integer DEFAULT NULL,
  licId integer DEFAULT NULL,
  stalling boolean NOT NULL DEFAULT FALSE,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT resource_licenses_ibfk_1 FOREIGN KEY (resId) REFERENCES resource (id),
  CONSTRAINT resource_licenses_ibfk_2 FOREIGN KEY (licId) REFERENCES license (id)
);
--;;
CREATE TABLE resource_refresh_period (
  id serial NOT NULL PRIMARY KEY,
  resId integer DEFAULT NULL,
  refreshPeriod integer DEFAULT NULL,
  modifierUserId varchar(255) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT resource_refresh_period_ibfk_1 FOREIGN KEY (resId) REFERENCES resource (id)
);
--;;
CREATE TABLE resource_state (
  id serial NOT NULL PRIMARY KEY,
  resId integer DEFAULT NULL,
  ownerUserId varchar(255) NOT NULL,
  modifierUserId varchar(255) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT resource_state_ibfk_1 FOREIGN KEY (resId) REFERENCES resource (id)
);
--;;
CREATE TABLE user_selection_names (
  id serial NOT NULL PRIMARY KEY,
  actionId bigint NOT NULL,
  groupId integer NOT NULL,
  listName varchar(32) DEFAULT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL
);
--;;
CREATE TABLE user_selections (
  id serial NOT NULL PRIMARY KEY,
  actionId bigint NOT NULL,
  groupId integer NOT NULL,
  userId varchar(255) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL
);
--;;
CREATE TABLE workflow_approver_options (
  id serial NOT NULL PRIMARY KEY,
  wfApprId integer DEFAULT NULL,
  keyValue varchar(256) NOT NULL,
  optionValue varchar(256) NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT workflow_approver_options_ibfk_1 FOREIGN KEY (wfApprId) REFERENCES workflow_actors (id)
);
--;;
CREATE TABLE workflow_licenses (
  id serial NOT NULL PRIMARY KEY,
  wfId integer DEFAULT NULL,
  licId integer DEFAULT NULL,
  round integer NOT NULL,
  stalling boolean NOT NULL DEFAULT FALSE,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT workflow_licenses_ibfk_1 FOREIGN KEY (wfId) REFERENCES workflow (id),
  CONSTRAINT workflow_licenses_ibfk_2 FOREIGN KEY (licId) REFERENCES license (id)
);
--;;
CREATE TABLE workflow_round_min (
  id serial NOT NULL PRIMARY KEY,
  wfId integer DEFAULT NULL,
  min integer NOT NULL,
  round integer NOT NULL,
  start timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp NULL DEFAULT NULL,
  CONSTRAINT workflow_round_min_ibfk_1 FOREIGN KEY (wfId) REFERENCES workflow (id)
);
--;;
-- TODO add foreign key constraints from other tables to user table
CREATE TABLE users (
  userId varchar(255) NOT NULL PRIMARY KEY,
  userAttrs jsonb
);
--;;
CREATE TABLE roles (
  -- TODO should this have an id for consistency with the other tables?
  userId varchar(255),
  role varchar(255),
  PRIMARY KEY (userId, role),
  FOREIGN KEY (userId) REFERENCES users
);
--;;
CREATE TABLE application_event (
  id serial NOT NULL PRIMARY KEY, -- for ordering events
  appId integer REFERENCES catalogue_item_application (id),
  userId varchar(255) REFERENCES users (userId),
  round integer NOT NULL,
  event application_event_type NOT NULL,
  comment varchar(4096) DEFAULT NULL,
  time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP
);
DROP TABLE catalogue_item_state;
--;;
ALTER TABLE catalogue_item ADD state item_state DEFAULT 'enabled';
CREATE TABLE entitlement_post_log (
  time timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  payload jsonb,
  status varchar(32)
);
ALTER TABLE resource
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE workflow
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE application_form
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE catalogue_item
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE catalogue_item_application
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE application_form_item
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE application_form_item_map
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE license
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE application_license_approval_values
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE application_text_values
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE workflow_actors
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE catalogue_item_application_free_comment_values
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE catalogue_item_application_licenses
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE catalogue_item_application_members
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE catalogue_item_application_metadata
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE catalogue_item_application_predecessor
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE catalogue_item_localization
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE entitlement
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE license_localization
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE resource_close_period
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE resource_licenses
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE resource_refresh_period
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE resource_state
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE user_selection_names
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE user_selections
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE workflow_approver_options
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE workflow_licenses
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE workflow_round_min
ALTER COLUMN start TYPE timestamp with time zone,
ALTER COLUMN endt TYPE timestamp with time zone;
--;;
ALTER TABLE application_event
ALTER COLUMN time TYPE timestamp with time zone;
--;;
ALTER TABLE entitlement_post_log
ALTER COLUMN time TYPE timestamp with time zone;
CREATE TABLE api_key (
  apiKey varchar(255) NOT NULL PRIMARY KEY,
  comment varchar(255)
);
ALTER TABLE catalogue_item ALTER COLUMN state SET NOT NULL;
alter table workflow
  add column prefix varchar(255) not null default 'default';
--;;
alter table workflow
  alter column prefix drop default;
alter table application_form
  add column prefix varchar(255) not null default 'default';
--;;
alter table application_form
  alter column prefix drop default;
alter table resource
  add column ownerUserId varchar(255);
--;;
update resource
  set ownerUserId = modifierUserId;
--;;
alter table resource
  alter column ownerUserId set not null;
CREATE TABLE application_attachments (
    id serial NOT NULL PRIMARY KEY,
    catAppId integer DEFAULT NULL,
    formMapId integer DEFAULT NULL,
    modifierUserId varchar(255) NOT NULL,
    filename varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    data bytea NOT NULL,
    start timestamp with time zone DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT application_attachments_ibfk_1 FOREIGN KEY (catAppId) REFERENCES catalogue_item_application (id),
    CONSTRAINT application_attachments_ibfk_2 FOREIGN KEY (formMapId) REFERENCES application_form_item_map (id),
    UNIQUE (catAppId, formMapId)
);
ALTER TABLE APPLICATION_FORM
RENAME COLUMN prefix to organization;
--;;
ALTER TABLE workflow
RENAME COLUMN prefix to organization;
--;;
ALTER TABLE resource
RENAME COLUMN prefix to organization;
ALTER TABLE application_event
  ADD COLUMN eventData jsonb;
ALTER TABLE application_event
  ALTER COLUMN event TYPE varchar(32);
--;;
DROP TYPE application_event_type CASCADE;
ALTER TABLE workflow ADD workflowBody jsonb;
-- :disable-transaction
-- IF NOT EXISTS because we don't have a corresponding down migration
ALTER TYPE itemtype
  ADD VALUE IF NOT EXISTS 'description';
ALTER TABLE catalogue_item_application
  ADD description VARCHAR(255);
ALTER TABLE application_form_item_map ADD COLUMN maxlength SMALLINT;
-- :disable-transaction
-- IF NOT EXISTS because we don't have a corresponding down migration
ALTER TYPE itemtype
  ADD VALUE IF NOT EXISTS 'option';
--;;
CREATE TABLE application_form_item_options
(
  itemId       integer      NOT NULL,
  key          varchar(255) NOT NULL,
  langCode     varchar(64)  NOT NULL,
  label        varchar(255) NOT NULL,
  displayOrder integer      NOT NULL,
  PRIMARY KEY (itemId, key, langCode),
  FOREIGN KEY (itemId) REFERENCES application_form_item (id)
);
-- :disable-transaction
-- IF NOT EXISTS because we don't have a corresponding down migration
ALTER TYPE itemtype
  ADD VALUE IF NOT EXISTS 'multiselect';
create unique index resource_resid_u on resource (organization, resid, coalesce(endt, '10000-01-01'));
CREATE TABLE license_attachment (
    id serial NOT NULL PRIMARY KEY,
    modifierUserId varchar(255) NOT NULL,
    filename varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    data bytea NOT NULL,
    start timestamp with time zone DEFAULT CURRENT_TIMESTAMP
);
--;;
ALTER TABLE license_localization ADD COLUMN attachmentId INTEGER REFERENCES license_attachment;
--;;
ALTER TABLE license ADD COLUMN attachmentId INTEGER REFERENCES license_attachment;ALTER TABLE application_form_item_localization
  ALTER COLUMN title TYPE varchar(4096);
--;;
alter table application_event
  alter column event type varchar(100);
DROP TABLE catalogue_item_application_free_comment_values;
--;;
DROP TABLE catalogue_item_application_members;
--;;
DROP TABLE catalogue_item_application_metadata;
--;;
DROP TABLE catalogue_item_application_predecessor;
DROP TABLE user_selections;
--;;
DROP TABLE user_selection_names;alter table catalogue_item
  add column enabled boolean default true not null ,
  add column archived boolean default false not null;
--;;
update catalogue_item
set enabled = (state = 'enabled');
--;;
alter table catalogue_item
  drop column state;
--;;
drop type item_state;
--;;
alter table resource
  add column enabled boolean default true not null ,
  add column archived boolean default false not null;
--;;
alter table application_form
  add column enabled boolean default true not null ,
  add column archived boolean default false not null;
--;;
alter table workflow
  add column enabled boolean default true not null ,
  add column archived boolean default false not null;
--;;
alter table license
  add column enabled boolean default true not null ,
  add column archived boolean default false not null;
CREATE TABLE poller_state (
  name varchar(64) primary key,
  state jsonb not null
);
CREATE TABLE form_template (
  id serial NOT NULL PRIMARY KEY,
  ownerUserId varchar(255) NOT NULL,
  modifierUserId varchar(255) NOT NULL,
  title varchar(256) NOT NULL,
  visibility scope NOT NULL,
  fields jsonb,
  start timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  endt timestamp with time zone NULL DEFAULT NULL,
  organization varchar(255) not null,
  enabled boolean default true not null,
  archived boolean default false not null
);
CREATE TABLE external_application_id (
  prefix varchar(64) NOT NULL,
  suffix varchar(64) NOT NULL,
  PRIMARY KEY (prefix, suffix)
);
--;;
delete
from roles
where role in ('applicant', 'approver', 'reviewer');
-- No migration for data!
CREATE TABLE attachment (
    id serial NOT NULL PRIMARY KEY,
    appId integer NOT NULL, -- link to application for access control
    modifierUserId varchar(255) NOT NULL,
    filename varchar(255) NOT NULL,
    type varchar(255) NOT NULL,
    data bytea NOT NULL,
    CONSTRAINT attachment_appid_fkey FOREIGN KEY (appId) REFERENCES catalogue_item_application (id)
);
--;;
DROP TABLE IF EXISTS application_attachments CASCADE;
--;;
ALTER TABLE catalogue_item
    ADD CONSTRAINT catalogue_item_ibfk_4 FOREIGN KEY (formId) REFERENCES form_template (id);
CREATE TABLE user_settings (
  userId varchar(255) NOT NULL PRIMARY KEY,
  settings jsonb,
  FOREIGN KEY (userId) REFERENCES users
);
-- begin generating form IDs using form_template_id_seq instead of application_form_id_seq
SELECT setval('form_template_id_seq', (SELECT nextval('application_form_id_seq')), false);
--;;
ALTER TABLE catalogue_item
    DROP CONSTRAINT catalogue_item_ibfk_3;
drop table workflow_round_min;
--;;
drop table workflow_approver_options;
--;;
drop table workflow_actors;
--;;
drop table resource_state;
--;;
drop table resource_refresh_period;
--;;
drop table resource_close_period;
--;;
drop table catalogue_item_application_licenses;
--;;
drop table catalogue_item_application_items;
--;;
drop table application_text_values;
--;;
drop table application_license_approval_values;
--;;
drop table application_form_item_options;
--;;
drop table application_form_item_map;
--;;
drop table application_form_item_localization;
--;;
drop table application_form_item;
--;;
drop table application_form;
alter table application_event
    drop column userid;
--;;
alter table application_event
    drop column round;
--;;
alter table application_event
    drop column event;
--;;
alter table application_event
    drop column comment;
--;;
alter table application_event
    drop column time;
--;;

alter table catalogue_item_application
    drop column applicantuserid;
--;;
alter table catalogue_item_application
    drop column start;
--;;
alter table catalogue_item_application
    drop column endt;
--;;
alter table catalogue_item_application
    drop column modifieruserid;
--;;
alter table catalogue_item_application
    drop column wfid;
--;;
alter table catalogue_item_application
    drop column description;
--;;

alter table catalogue_item_localization
    drop column start;
--;;
alter table catalogue_item_localization
    drop column endt;
--;;

alter table form_template
    drop column visibility;
--;;

alter table license
    drop column attid;
--;;
alter table license
    drop column visibility;
--;;

alter table license_localization
    drop column attid;
--;;
alter table license_localization
    drop column start;
--;;
alter table license_localization
    drop column endt;
--;;

alter table resource_licenses
    drop column stalling;
--;;

alter table workflow
    drop column fnlround;
--;;
alter table workflow
    drop column visibility;
--;;

alter table workflow_licenses
    drop column round;
--;;
alter table workflow_licenses
    drop column stalling;
--;;
-- Add localizations for catalogue items with a title but missing localizations.
INSERT INTO catalogue_item_localization(catid, title, langcode)
SELECT ci.id, ci.title, 'en'
FROM catalogue_item ci FULL OUTER JOIN catalogue_item_localization loc
ON ci.id=loc.catid
WHERE loc.catid IS NULL;
--;;
ALTER TABLE catalogue_item DROP COLUMN title;
ALTER TABLE license
  DROP COLUMN start,
  DROP COLUMN endt;
--;;
ALTER TABLE resource_licenses
  DROP COLUMN start,
  DROP COLUMN endt;
--;;
ALTER TABLE workflow_licenses
  DROP COLUMN start,
  DROP COLUMN endt;
ALTER TABLE license DROP COLUMN title;
--;;
ALTER TABLE license DROP COLUMN textcontent;
--;;
ALTER TABLE license DROP COLUMN attachmentid;
ALTER TABLE catalogue_item_localization
  ADD COLUMN infoUrl varchar(1024);
ALTER TABLE catalogue_item_localization
  ADD CONSTRAINT catalogue_item_localization_unique UNIQUE (catid, langcode);
CREATE TABLE blacklist_event (
  id serial NOT NULL PRIMARY KEY,
  eventdata jsonb
);
--;;
CREATE INDEX index_blacklist_event_resource_user ON blacklist_event(
  (eventdata->>'blacklist/resource'),
  (eventdata->>'blacklist/user')
);
ALTER TABLE workflow
DROP COLUMN start,
DROP COLUMN endt;
--;;
ALTER TABLE resource
DROP COLUMN start,
DROP COLUMN endt;
--;;
ALTER TABLE form_template
DROP COLUMN start,
DROP COLUMN endt;
--;;
CREATE UNIQUE INDEX resource_resid_u ON resource (organization, resid);
ALTER TABLE api_key
ADD COLUMN permittedRoles jsonb NOT NULL DEFAULT '["applicant", "decider", "handler", "logged-in", "owner", "past-reviewer", "reporter", "reviewer"]'::jsonb;
CREATE TABLE email_outbox
(
    id             serial      NOT NULL PRIMARY KEY,
    email          jsonb       NOT NULL,
    created        timestamptz NOT NULL DEFAULT now(),
    latest_attempt timestamptz NULL     DEFAULT NULL,
    latest_error   text        NOT NULL DEFAULT '',
    next_attempt   timestamptz NULL     DEFAULT now(),
    backoff        interval    NOT NULL DEFAULT interval '1 second'
        -- zero interval would disable exponential backoff (and negative interval would be just wrong)
        CONSTRAINT minimum_backoff CHECK ( backoff >= interval '1 second' ),
    deadline       timestamptz NOT NULL DEFAULT now()
);
DROP TABLE poller_state;
CREATE TABLE outbox (
  id serial NOT NULL PRIMARY KEY,
  outboxData jsonb NOT NULL
);
-- no migration from email_outbox to outbox: all pending emails will be lost
DROP TABLE email_outbox;
DROP TABLE entitlement_post_log;
UPDATE application_event
SET eventdata = jsonb_set(eventdata, '{workflow/type}', '"workflow/default"')
WHERE eventdata ->> 'event/type' = 'application.event/created'
  AND eventdata ->> 'workflow/type' = 'workflow/dynamic';
--;;
UPDATE workflow
SET workflowbody = jsonb_set(workflowbody, '{type}', '"workflow/default"')
WHERE workflowbody ->> 'type' = 'workflow/dynamic';
UPDATE application_event
SET eventdata = jsonb_set(eventdata, '{event/type}', '"application.event/reviewed"')
WHERE eventdata ->> 'event/type' = 'application.event/commented';
--;;
UPDATE application_event
SET eventdata =
            jsonb_set(
                    jsonb_set(eventdata,
                              '{event/type}', '"application.event/review-requested"'),
                    '{application/reviewers}', eventdata -> 'application/commenters')
            - 'application/commenters'
WHERE eventdata ->> 'event/type' = 'application.event/comment-requested';
ALTER TABLE catalogue_item
ADD COLUMN organization varchar(255);
--;;
UPDATE catalogue_item SET organization = '';
--;;
ALTER TABLE catalogue_item
ALTER COLUMN organization SET NOT NULL;
ALTER TABLE license
ADD COLUMN organization varchar(255);
--;;
UPDATE license SET organization = '';
--;;
ALTER TABLE license
ALTER COLUMN organization SET NOT NULL;
drop index resource_resid_u;
ALTER TABLE entitlement ADD COLUMN approvedby varchar(255);
--;;
ALTER TABLE entitlement ADD COLUMN revokedby varchar(255);
CREATE TABLE organization (
  id VARCHAR(255) NOT NULL PRIMARY KEY,
  modifierUserId varchar(255) NOT NULL,
  modified timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  data jsonb NOT NULL
);
ALTER TABLE api_key DROP COLUMN permittedRoles;
ALTER TABLE api_key
  ADD COLUMN users jsonb DEFAULT NULL,
  ADD COLUMN paths jsonb DEFAULT NULL;
CREATE TABLE audit_log (
  time timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
  path varchar(255) NOT NULL,
  method varchar(10) NOT NULL,
  apiKey varchar(255),
  userid varchar(255),
  status varchar(10) NOT NULL
);
--;;
CREATE INDEX audit_log_time ON audit_log (time);
--;;
CREATE INDEX audit_log_userid ON audit_log (userid);
DELETE FROM roles WHERE role = 'organization-owner';
ALTER TABLE audit_log ALTER COLUMN time DROP DEFAULT;
--;;
ALTER TABLE catalogue_item ALTER COLUMN start DROP DEFAULT;
--;;
ALTER TABLE entitlement ALTER COLUMN start DROP DEFAULT;
--;;
ALTER TABLE license_attachment ALTER COLUMN start DROP DEFAULT;
--;;
ALTER TABLE organization ALTER COLUMN modified DROP DEFAULT;
--;;
ALTER TABLE form_template ADD formdata jsonb;
--;;
UPDATE form_template SET formdata = jsonb '{"form/external-title": {}}' || jsonb_build_object('form/internal-name', title);
--;;
ALTER TABLE form_template DROP COLUMN title;
CREATE TABLE user_secrets (
  userId varchar(255) NOT NULL PRIMARY KEY,
  secrets jsonb,
  FOREIGN KEY (userId) REFERENCES users
);
ALTER TABLE catalogue_item
  ALTER COLUMN formid DROP NOT NULL;
CREATE TABLE invitation (
  id serial NOT NULL PRIMARY KEY,
  invitationdata jsonb NOT NULL
);
ALTER TABLE resource ADD COLUMN resourcedata jsonb;
CREATE TABLE category (
  id serial NOT NULL PRIMARY KEY,
  categorydata jsonb
);
--;;
ALTER TABLE catalogue_item
 ADD COLUMN catalogueitemdata jsonb;ALTER TABLE resource
DROP COLUMN IF EXISTS ownerUserId,
DROP COLUMN IF EXISTS modifierUserId;
--;;
ALTER TABLE form_template
DROP COLUMN IF EXISTS ownerUserId,
DROP COLUMN IF EXISTS modifierUserId;
--;;
ALTER TABLE license
DROP COLUMN IF EXISTS ownerUserId,
DROP COLUMN IF EXISTS modifierUserId;
--;;
ALTER TABLE workflow
DROP COLUMN IF EXISTS ownerUserId,
DROP COLUMN IF EXISTS modifierUserId;
--;;
ALTER TABLE organization
DROP COLUMN IF EXISTS modifierUserId,
DROP COLUMN IF EXISTS modified;
--;;
ALTER TABLE attachment
RENAME COLUMN modifierUserId TO userId;
--;;
ALTER TABLE license_attachment
RENAME COLUMN modifierUserId TO userId;
CREATE TABLE user_mappings (
  userId varchar(255) NOT NULL,
  extIdAttribute varchar(255) NOT NULL,
  extIdValue varchar(255) NOT NULL,
  PRIMARY KEY (userId, extIdAttribute, extIdValue),
  FOREIGN KEY (userId) REFERENCES users
);
UPDATE organization SET data = data - 'organization/modifier' - 'organization/last-modified';
DROP TABLE IF EXISTS workflow_licenses;
