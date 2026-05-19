-- WP4 enables real Word/PDF/OCR document import source types.

alter table document_input_source
    drop constraint if exists ck_document_input_source_type;

alter table document_input_source
    add constraint ck_document_input_source_type check (source_type in (
        'TEXT','MARKDOWN','WORD','PDF','OCR','CONFLUENCE','FEISHU','DINGTALK','YUQUE','CUSTOM_API'
    ));

alter table document_input_import
    drop constraint if exists ck_document_input_import_type;

alter table document_input_import
    add constraint ck_document_input_import_type check (source_type in (
        'TEXT','MARKDOWN','WORD','PDF','OCR','CONFLUENCE','FEISHU','DINGTALK','YUQUE','CUSTOM_API'
    ));
