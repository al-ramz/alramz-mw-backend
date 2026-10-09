# Azure Infrastructure Deployment Commands

This document contains all Azure commands required to deploy the infrastructure sequentially.

---

## Prerequisites

```bash
# Login to Azure
az login

# Set subscription (if needed)
az account set --subscription "<subscription-id>"

# Verify subscription
az account show
```

---

## Step 1: Create Terraform State Storage

Run these commands once to create the storage account for Terraform state files.

```bash
# Variables
RESOURCE_GROUP="alramz-tf-assets-rg"
LOCATION="uaenorth"
STORAGE_ACCOUNT="alramztfstatefiles1994"
SUBSCRIPTION_ID="c65cd71d-a01c-4f33-a04f-3163f61a94c6"

APP_ID=$(az ad app create \
  --display-name "github-actions-terraform" \
  --query appId \
  -o tsv)

# Create resource group for Terraform state
az group create \
  --name $RESOURCE_GROUP \
  --location $LOCATION

az account set --subscription c65cd71d-a01c-4f33-a04f-3163f61a94c6

# Create storage account
az storage account create \
  --name $STORAGE_ACCOUNT \
  --resource-group $RESOURCE_GROUP \
  --location $LOCATION \
  --sku Standard_LRS \
  --kind StorageV2

# Get storage account key
ACCOUNT_KEY=$(az storage account keys list \
  --account-name $STORAGE_ACCOUNT \
  --resource-group $RESOURCE_GROUP \
  --query '[0].value' -o tsv)

# Create containers for each environment
az storage container create \
  --name sharedplatformtfstate \
  --account-name $STORAGE_ACCOUNT \
  --account-key $ACCOUNT_KEY

az storage container create \
  --name devtfstate \
  --account-name $STORAGE_ACCOUNT \
  --account-key $ACCOUNT_KEY

az storage container create \
  --name qatfstate \
  --account-name $STORAGE_ACCOUNT \
  --account-key $ACCOUNT_KEY

az storage container create \
  --name preprodtfstate \
  --account-name $STORAGE_ACCOUNT \
  --account-key $ACCOUNT_KEY

az storage container create \
  --name prodtfstate \
  --account-name $STORAGE_ACCOUNT \
  --account-key $ACCOUNT_KEY
```

az vm list-skus \
  --location uaenorth \
  --resource-type virtualMachines \
  --query "[?contains(name, 'B2as_v2')].name" \
  -o tsv

TF_SP_CLIENT_ID="da04e03f-2352-4748-b09e-d03c58600b0f"

az ad sp show --id $TF_SP_CLIENT_ID

az role assignment create \
  --assignee $TF_SP_CLIENT_ID \
  --role Contributor \
  --scope /subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RESOURCE_GROUP


az role assignment create \
  --assignee "bb1d4698-8b38-4375-ae5f-1a0dcd4f131e" \
  --role Contributor \
  --scope "/subscriptions/$SUBSCRIPTION_ID"

SUBSCRIPTION_ID="c65cd71d-a01c-4f33-a04f-3163f61a94c6"
RG_NAME="alramz-shared-platform-rg"
SP_OBJECT_ID="f5095702-ee3b-4ff2-956d-61e64a63443b"

az role assignment create \
  --assignee-object-id "$SP_OBJECT_ID" \
  --assignee-principal-type ServicePrincipal \
  --role "User Access Administrator" \
  --scope "/subscriptions/$SUBSCRIPTION_ID/resourceGroups/$RG_NAME"

 az role assignment list \
  --assignee-object-id "f5095702-ee3b-4ff2-956d-61e64a63443b" \
  --all \
  --query "[].{Role:roleDefinitionName,Scope:scope}" \
  -o table
Role                       Scope
-------------------------  --------------------------------------------------------------------------------------------
Contributor                /subscriptions/c65cd71d-a01c-4f33-a04f-3163f61a94c6/resourceGroups/alramz-tf-assets-rg
Contributor                /subscriptions/c65cd71d-a01c-4f33-a04f-3163f61a94c6
User Access Administrator  /subscriptions/c65cd71d-a01c-4f33-a04f-3163f61a94c6/resourceGroups/alramz-shared-platform-rg
User Access Administrator  /subscriptions/c65cd71d-a01c-4f33-a04f-3163f61a94c6/resourceGroups/alramz-dev-rg
User Access Administrator  /subscriptions/c65cd71d-a01c-4f33-a04f-3163f61a94c6



---

## Step 2: Deploy Shared Platform Stack

The shared platform stack creates the Azure Container Registry (ACR) and GitHub Actions service principal.

```bash
# Navigate to shared platform stack
cd infra/stacks/shared-platform

# Initialize Terraform
terraform init

# Validate configuration
terraform validate

# Plan deployment
terraform plan \
  -var-file="../../environments/shared-platform.tfvars" \
  -out=tfplan

# Apply deployment
terraform apply tfplan

# Capture outputs
terraform output -json
```

**Save these outputs for GitHub secrets:**
- `github_actions_client_id` → `AZURE_CLIENT_ID`
- `github_actions_client_secret` → `AZURE_CREDENTIALS`
- `subscription_id` → `AZURE_SUBSCRIPTION_ID`
- `tenant_id` → `AZURE_TENANT_ID`

---

## Step 3: Configure GitHub Secrets

```bash
# Set repository secrets using GitHub CLI
gh auth login

# Azure credentials from shared platform stack
gh secret set AZURE_CLIENT_ID --body "<github_actions_client_id>"
gh secret set AZURE_SUBSCRIPTION_ID --body "<subscription_id>"
gh secret set AZURE_TENANT_ID --body "<tenant_id>"

# Additional secrets
gh secret set GH_TOKEN --body "<personal-access-token>"
gh secret set DOCKERHUB_USERNAME --body "<dockerhub-username>"
gh secret set DOCKERHUB_PASSWORD --body "<dockerhub-token>"
```

---

## Step 4: Deploy Dev Environment

```bash
# Navigate to dev stack
cd infra/stacks/dev

# Initialize Terraform
terraform init

# Validate configuration
terraform validate

# Plan deployment
terraform plan \
  -var-file="../../environments/dev.tfvars" \
  -out=tfplan

# Apply deployment
terraform apply tfplan

# Capture outputs
terraform output -json
```

**After deployment, update GitHub secrets with identity client ID:**

```bash
# Get platform identity client ID
CLIENT_ID=$(terraform output -raw platform_identity_client_id)

# Update GitHub secret
gh secret set MANAGED_IDENTITY_CLIENT_ID_DEV --body "$CLIENT_ID"
```

---

## Step 5: Verify Deployment

```bash
# Configure kubectl for AKS
az aks get-credentials \
  --resource-group alramz-dev-rg \
  --name alramz-dev-aks \
  --overwrite-existing

# Verify cluster
kubectl get nodes

# Verify ACR
az acr show --name alramzregistry

# Verify APIM
az apim show --name alramz-dev-apim --resource-group alramz-dev-rg

# Verify Key Vault
az keyvault show --name alramz-dev-key-vault

# Verify platform identity
az identity show \
  --name alramz-dev-platform-identity \
  --resource-group alramz-dev-rg
```

---

## Step 6: Deploy Other Environments

Repeat Step 4 for each environment:

```bash
# QA Environment
cd infra/stacks/qa
terraform init
terraform plan -var-file="../../environments/qa.tfvars" -out=tfplan
terraform apply tfman

# Preprod Environment
cd infra/stacks/preprod
terraform init
terraform plan -var-file="../../environments/preprod.tfvars" -out=tfplan
terraform apply tfplan

# Prod Environment
cd infra/stacks/prod
terraform init
terraform plan -var-file="../../environments/prod.tfvars" -out=tfplan
terraform apply tfplan
```

---

## Terraform State Backend Configuration

| Stack | Container | Key |
|-------|-----------|-----|
| shared-platform | `sharedplatformtfstate` | `sharedplatform.tfstate` |
| dev | `devtfstate` | `dev.tfstate` |
| qa | `qatfstate` | `qa.tfstate` |
| preprod | `preprodtfstate` | `preprod.tfstate` |
| prod | `prodtfstate` | `prod.tfstate` |

---

## Common Commands

```bash
# Format Terraform files
terraform fmt -recursive

# Validate all stacks
for stack in dev qa preprod prod; do
  cd infra/stacks/$stack
  terraform validate
  cd ../..
done

# Destroy a stack (use with caution)
cd infra/stacks/dev
terraform destroy -var-file="../../environments/dev.tfvars"

# Refresh state
terraform refresh -var-file="../../environments/dev.tfvars"

# Show current state
terraform state list

# Import existing resource
terraform import azurerm_resource_group.example /subscriptions/xxx/resourceGroups/xxx
```

---

## Troubleshooting

```bash
# Enable Terraform logging
export TF_LOG=TRACE
export TF_LOG_PATH=terraform.log

# Check Azure provider version
terraform providers

# Lock file issues
rm .terraform.lock.hcl
terraform init

# State lock issues
terraform force-unlock <LOCK_ID>

# Verify Azure login
az account show

# Check resource group exists
az group show --name alramz-dev-rg
```
