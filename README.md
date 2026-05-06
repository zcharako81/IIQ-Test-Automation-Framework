# SailPoint IdentityIQ Test Automation Framework

A modular and extensible test automation framework for **SailPoint IdentityIQ (IIQ)** using **SCIM APIs**, **TestNG**, and **REST-Assured**.
It can be executed standalone or integrated to a DevOps pipeline. 

This framework supports end-to-end IAM testing including:

* ✅ Create Identity / Modify Identity / Delete Identity
* ✅ Verify Identity attributes
* ✅ Launch Workflows
* ✅ Launch Tasks (via Workflow)
* ✅ Verify Birthright Role assignments
* ✅ Account Provisioning 
* ✅ Verify Accounts
---

## 🧱 Tech Stack

| Component        | Technology |
|-----------------|-----------|
| Language        | Java 11+ |
| Build Tool      | Maven |
| Test Framework  | TestNG |
| API Testing     | REST-Assured |
| Configuration   | Properties files |
---

## 📁 Project Structure
```
src/test/java
│
├── base/                # Core framework classes (API, config, auth)
├── model/               # SCIM models (Identity, Workflow, etc.)
├── services/            # API service layer (Identity, Workflow)
├── testDataFactory/     # Test data builders
├── utils/               # Helper utilities (waits, validation)
├── test/                # <-- here to define your test cases
│   ├── identity/        # Identity lifecycle tests
│   ├── connectors/      # Connector-specific tests (e.g., LDAP)
│   └── base/            # Base test classes
│
src/test/resources
├── config.properties    # general test config
├── identity.properties  # identity attributes for creation and verification
├── account.properties   # account attributes of various applicatons can be added. 
src/test/iiq
│
├── config/My-WF-TaskLauncher	#Workflow that is called via SCIM for the Task execution   
```
---

## 👉 Instructions
- Workflow for Task execution `My-WF-TaskLauncher` must be imported into IIQ, before tests can be executed. 
- Task names can be changed in the `config.properties` file (e.g. for Identity Refresh or Account Aggregation). Name of the test identity will be passed to the as Task filter to reduce the execution time. 
- Change the identity and account attributes in the `identity.properties` and `account.properties` files to your needs.
- Adjust and execute the predefined test cases in `src/test/test/identityTest.java.`

## ⚙️ Configuration

### Global config

`config.properties`
```
base.url=http://localhost:8080/identityiq
username=REPLACE_ME
password=REPLACE_ME
task.name1=RefreshIdentitySingle
task.name2=LdapAccountAggregation
```

### Identity test data
`identity.properties`

### Account validation (multi-application)
`account.properties`

---

## 🧪 Test Execution

Run all tests:

```bash
mvn clean test
```
Or via TestNG suite:
```
mvn test -DsuiteXmlFile=Testng.xml
```

---
## 📦 Dependencies

Defined in `pom.xml`:

- `org.testng:testng`
- `io.rest-assured:rest-assured`
- `org.apache.commons:commons-lang3` (if used)
- (Optional) JSON serializer like Jackson (if added later)

---
🧠 Design Principles

* Separation of concerns (model / service / test)
* Config-driven test data
* SCIM-first approach
* Reusable utilities
* Extensible for multiple connectors
---
👤 Author: Zisis Charakopidis (zisis.charakopidis@icloud.com)
