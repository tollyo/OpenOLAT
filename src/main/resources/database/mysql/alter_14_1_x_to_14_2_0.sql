-- Assessment
alter table o_as_entry add a_first_visit datetime;
alter table o_as_entry add a_last_visit datetime;
alter table o_as_entry add a_num_visits int8;
alter table o_as_entry add a_date_done datetime;