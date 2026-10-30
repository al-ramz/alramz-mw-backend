--liquibase formatted sql
--changeset gaurav:006-notification-sms-configurations

CREATE TABLE IF NOT EXISTS sms_configurations (
    id VARCHAR(20) NOT NULL,
    number_of_placeholders INTEGER,
    max_attempts INTEGER,
    en_body VARCHAR(1000),
    ar_body VARCHAR(1000),
    is_bulk VARCHAR(1),
    template_name VARCHAR(50),
    mask_placeholders VARCHAR(1),
    CONSTRAINT sms_configurations_pk PRIMARY KEY (id),
    CONSTRAINT sms_configurations_template_name_uk UNIQUE (template_name),
    CONSTRAINT sms_configurations_placeholders_ck CHECK (number_of_placeholders IS NULL OR number_of_placeholders >= 0),
    CONSTRAINT sms_configurations_max_attempts_ck CHECK (max_attempts IS NULL OR max_attempts >= 1),
    CONSTRAINT sms_configurations_is_bulk_ck CHECK (is_bulk IN ('0', '1')),
    CONSTRAINT sms_configurations_mask_ck CHECK (mask_placeholders IN ('0', '1'))
);

INSERT INTO sms_configurations (id, number_of_placeholders, max_attempts, en_body, ar_body, is_bulk, template_name, mask_placeholders) VALUES
('82', 1, 1000, 'An amount of AED %1% has been deposited in your account.', 'An amount of AED %1% has been deposited in your account.', '0', 'CASHIN_NOTIFICATION_CONFIRMATION', '0'),
('83', 1, 1000, 'Your fund transfer request of amount AED %1% has expired and closed due to non-receipt of the transfer.', 'Your fund transfer request of amount AED %1% has expired and closed due to non-receipt of the transfer.', '0', 'CASHIN_NOTIFICATION_EXPIRY', '0'),
('22', 0, 1000, 'Your registered mobile number has been updated successfully.', 'Your Mobile Number has been changed', '0', 'OTP_CHANGE_MOBILE_CONFIRMATION', '0'),
('24', 0, 1000, 'Your bank details have been updated successfully.', 'Your bank details have been updated successfully.', '0', 'OTP_CHANGE_BANK_DETAILS_CONFIRMATION', '0'),
('61', 1, 1000, 'The temporary password of your Al Ramz account is %1%', 'The temporary password for your Al Ramz account is %1%', '0', 'DFM_ONBOARDING_PASSWORD', '1'),
('1', 1, 5, 'sending test %1% SMS', 'sending test %1% SMS', '0', 'TEST_SMS', '0'),
('2', 2, 10, 'Your subscription for %1% IPO has been received successfully. Kindly note your reference number %2% for future inquiries.', 'Your subscription for %1% IPO has been received successfully. Kindly note your reference number %2% for future inquiries.', '0', 'IPO_CONFIGURATION', '0'),
('3', 0, 1000, 'Your registered address has been updated successfully.', 'Your registered address has been updated successfully.', '0', 'OTP_CHANGE_ADDRESS_CONFIRMATION', '0'),
('41', 0, 1000, 'Your password was successfully changed. If not requested, please call 800-Ramz(7269) immediately', 'Your password was successfully changed. If not requested, please call 800-Ramz(7269) immediately', '0', 'CHANGE_PASSWORD_ACKNOWLEDGEMENT', '0'),
('42', 0, 1000, 'Your password was successfully updated. If not requested, please call 800-Ramz(7269) immediately', 'Your password was successfully updated. If not requested, please call 800-Ramz(7269) immediately', '0', 'FORGOT_PASSWORD_ACKNOWLEDGEMENT', '0')
ON CONFLICT DO NOTHING;

--rollback DROP TABLE IF EXISTS sms_configurations;
