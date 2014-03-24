/*
 * Copyright (c) 2012-2014 NEC Corporation
 * All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 */

#ifndef UNC_UPLL_VTN_MOMGR_H
#define UNC_UPLL_VTN_MOMGR_H

#include <string>
#include <set>
#include <list>
#include "momgr_impl.hh"

namespace unc {
namespace upll {
namespace kt_momgr {

using unc::upll::config_momgr::CtrlrCommitStatus;
using unc::upll::config_momgr::CtrlrVoteStatus;
using unc::upll::dal::DalBindInfo;
using unc::upll::dal::DalDmlIntf;
using unc::upll::dal::DalCursor;
using unc::upll::dal::DalCDataType;
using unc::upll::ipc_util::IpctSt;


class VtnMoMgr : public MoMgrImpl {
 private:
  static unc_key_type_t vtn_child[];
  static BindInfo vtn_bind_info[];
  static BindInfo vtn_rename_bind_info[];
  static BindInfo vtn_controller_bind_info[];
  static BindInfo key_vtn_ctrlrtbl_bind_info[];
  static BindInfo key_vtn_maintbl_bind_info[];
  static BindInfo key_vtn_renametbl_bind_info[];

  upll_rc_t ValidateAttribute(ConfigKeyVal *kval,
                              DalDmlIntf *dmi,
                              IpcReqRespHeader *req = NULL);
  /**
   * @brief      Method to check if individual portions of a key are valid
   *
   * @param[in/out]  ikey                 pointer to ConfigKeyVal referring to a UNC resource
   * @param[in]      index                db index associated with the variable
   *
   * @retval         true                 input key is valid
   * @retval         false                input key is invalid.
   **/
  bool IsValidKey(void *key, uint64_t index);

  /**
   * @brief  Filters the attributes which need not be sent to controller
   *
   * @param[in/out]  val1   first record value instance.
   * @param[in]      val2   second record value instance.
   * @param[in]      audit  Not used for VTN
   * @param[in]      op     Operation to be performed
   *
   **/
  bool FilterAttributes(void *&val1, void *val2, bool audit_status,
                        unc_keytype_operation_t op);
  bool IsAllInvalidAttributes(const val_vtnstation_controller_st *val);

  /** Not used for VTN **/
  bool CompareValidValue(void *&val1, void *val2, bool audit);
  /*
   * @brief      Method to get a configkeyval of the parent keytype
   *
   * @param[in/out]  pkey           pointer to parent ConfigKeyVal
   * @param[in]      ck_vtn         pointer to the child configkeyval from
   * which the parent configkey val is obtained.
   *
   * @retval         UPLL_RC_SUCCESS      Successfull completion.
   * @retval         UPLL_RC_ERR_GENERIC  Failure case.
   **/
  upll_rc_t GetParentConfigKey(ConfigKeyVal *&pkey, ConfigKeyVal *ck_vtn) {
    return UPLL_RC_ERR_GENERIC;
  }
  upll_rc_t SwapKeyVal(ConfigKeyVal *ikey, ConfigKeyVal *&okey,
                       DalDmlIntf *dmi, uint8_t *ctrlr, bool &no_rename);
  upll_rc_t UpdateVtnConfigStatus(ConfigKeyVal *vtn_key,
                                  unc_keytype_operation_t op,
                                  uint32_t driver_result,
                                  ConfigKeyVal *nreq, DalDmlIntf *dmi);
  upll_rc_t UpdateConfigStatus(ConfigKeyVal *req, unc_keytype_operation_t op,
                               uint32_t driver_result, ConfigKeyVal *upd_key,
                               DalDmlIntf *dmi, ConfigKeyVal *ctrlr_key);
  /**
   * @brief  Update config status for commit result and vote result.
   *
   * @param[in/out]  ckv_running  ConfigKeyVal instance.
   * @param[in]      cs_status    either UNC_CS_INVALID or UNC_CS_APPLIED.
   * @param[in]      phase        specify the phase (CREATE,DELETE or UPDATE)
   * @param[in]      dmi          Pointer to the DalDmlIntf(DB Interface)
   *
   **/
  upll_rc_t UpdateAuditConfigStatus(unc_keytype_configstatus_t cs_status,
                                    uuc::UpdateCtrlrPhase phase,
                                    ConfigKeyVal *&ckv_running,
                                    DalDmlIntf *dmi);
  upll_rc_t UpdateCtrlrConfigStatus(unc_keytype_configstatus_t cs_status,
                                    uuc::UpdateCtrlrPhase phase,
                                    ConfigKeyVal *&ckv_running);
  upll_rc_t TxUpdateProcess(ConfigKeyVal *ck_main, IpcResponse *resp,
                            unc_keytype_operation_t op,
                            DalDmlIntf *dmi, controller_domain *ctrlr_dom,
                            set<string> *affected_ctrlr_set, bool *driver_resp);
  /**
   * @brief  Allocates for the specified val in the given configuration in the     * specified table.
   *
   * @param[in/out]  ck_val   Reference pointer to configval structure
   *                          allocated.
   * @param[in]      dt_type  specifies the configuration candidate/running/
   *                          state
   * @param[in]      tbl      specifies if the corresponding table is the
   *                          main table / controller table or rename table.
   *
   * @retval         UPLL_RC_SUCCESS      Successfull completion.
   * @retval         UPLL_RC_ERR_GENERIC  Failure case.
   **/
  upll_rc_t AllocVal(ConfigVal *&ck_val, upll_keytype_datatype_t dt_type,
                     MoMgrTables tbl = MAINTBL);
  /**
   * @brief  Gets the valid array position of the variable in the value
   *         structure from the table in the specified configuration
   *
   * @param[in]     val      pointer to the value structure
   * @param[in]     indx     database index for the variable
   * @param[out]    valid    position of the variable in the valid array -
   *                          NULL if valid does not exist.
   * @param[in]     dt_type  specifies the configuration
   * @param[in]     tbl      specifies the table containing the given value
   *
   **/
  upll_rc_t GetValid(void *val, uint64_t indx, uint8_t *&valid,
                     upll_keytype_datatype_t dt_type, MoMgrTables tbl);
  /**
   * @brief  Duplicates the input configkeyval including the key and val.
   * based on the tbl specified.
   *
   * @param[in]  okey   Output Configkeyval - allocated within the function
   * @param[in]  req    Input ConfigKeyVal to be duplicated.
   * @param[in]  tbl    specifies if the val structure belongs to the main table/ controller table or rename table.
   *
   * @retval         UPLL_RC_SUCCESS      Successfull completion.
   * @retval         UPLL_RC_ERR_GENERIC  Failure case.
   **/
  upll_rc_t DupConfigKeyVal(ConfigKeyVal *&okey, ConfigKeyVal *&req,
                            MoMgrTables tbl = MAINTBL);
  upll_rc_t MappingvExtTovBr(ConfigKeyVal *ikey, IpcReqRespHeader *req,
                             DalDmlIntf *dmi, uint32_t *&count);

  upll_rc_t ReadSingleCtlrlStation(IpcReqRespHeader *header,
                                   ConfigKeyVal *ikey,
                                   DalDmlIntf *dmi,
                                   uint32_t *count);
  upll_rc_t ReadSingleCtlrlVtnMapping(IpcReqRespHeader *header,
                                      ConfigKeyVal *ikey,
                                      DalDmlIntf *dmi,
                                      uint32_t *count);
  /**
   * @brief     Method used in Delete opertaion. Its semantic checks
   *            for the VTN key.
   *
   * @param[in]  ikey                        key and value structure.
   * @param[in]  dt_type                     key  type.
   * @param[in]  dmi                         Pointer to DalDmlIntf Class.
   *
   * @retval     UPLL_RC_SUCCESS             Successfull completion.
   * @retval     UPLL_RC_ERR_GENERIC         Failure case.
   * @retval     UPLL_RC_ERR_CFG_SEMANTIC    Failue dueto Semantic.
   */
  upll_rc_t IsReferenced(ConfigKeyVal *ikey, upll_keytype_datatype_t dt_type,
                         DalDmlIntf *dmi);
  /**
   * @brief     Method used in Delete opertaion. Its Create the Vtunnel
   *            Configkey and copy the VTN name from the ikey to Vtunnel
   *            Value structure.
   *
   * @param[in]  ikey                        key and value structure.
   * @param[in]  dt_type                     key  type.
   * @param[in]  dmi                         Pointer to DalDmlIntf Class.
   *
   * @retval     UPLL_RC_SUCCESS             Successfull completion.
   * @retval     UPLL_RC_ERR_GENERIC         Failure case.
   * @retval     UPLL_RC_ERR_CFG_SEMANTIC    Failue dueto Semantic.
   */

  upll_rc_t CreateVtunnelKey(ConfigKeyVal *ikey, ConfigKeyVal *&okey);

  /**
   * @Brief   Validates the syntax of the specified key and value structure
   *          for KT_VTN keytype
   *
   * @param[in]  req    This structure contains
   *                     IpcReqRespHeader(first 8 fields of
   *                     input request structure).
   * @param[in]  ikey   ikey contains key and value structure.
   *
   * @retval   UPLL_RC_SUCCESS               Successful.
   * @retval   UPLL_RC_ERR_CFG_SYNTAX        Syntax error.
   * @retval   UPLL_RC_ERR_NO_SUCH_INSTANCE  key_vtn is not available.
   * @retval   UPLL_RC_ERR_GENERIC           Generic failure.
   * @retval   UPLL_RC_ERR_INVALID_OPTION1   option1 is not valid.
   * @retval   UPLL_RC_ERR_INVALID_OPTION2   option2 is not valid.
   */
  upll_rc_t ValidateMessage(IpcReqRespHeader *req, ConfigKeyVal *kval);

  /**
   * @Brief  Checks if the specified key type(KT_VTN) and
   *         associated attributes are supported on the given controller,
   *         based on the valid flag
   *
   * @param[in]  req               This structure contains IpcReqRespHeader
   *                               (first 8 fields of input request structure).
   * @param[in]  ikey              ikey contains key and value structure.
   *
   * @retval   UPLL_RC_SUCCESS               Validation succeeded.
   * @retval   UPLL_RC_ERR_GENERIC           Validation failure.
   * @retval   UPLL_RC_ERR_NOT_SUPPORTED_BY_CTRLR  Attribute not supported.
   */
  upll_rc_t ValidateCapability(IpcReqRespHeader *req, ConfigKeyVal *ikey,
                               const char *cntrl_id = NULL);

  /**
   * @Brief  Checks if the specified key type and
   *         associated attributes are supported on the given controller,
   *         based on the valid flag.
   *
   * @param[in]  vtn_val         KEY_VTN value structure.
   * @param[in]  attrs           List of supported attribute from CAPA API.
   * @param[in]  operation       Operation name.
   *
   * @retval  UPLL_RC_SUCCESS                      validation succeeded.
   * @retval  UPLL_RC_ERR_NOT_SUPPORTED_BY_CTRLR   Attribute not supported.
   * @retval  UPLL_RC_ERR_GENERIC                  Generic failure.
   */
  upll_rc_t ValVtnAttributeSupportCheck(val_vtn_t *vtn_val,
                                        const uint8_t *attrs,
                                        uint32_t operation);

  /**
   * @Brief  Validates the syntax for KT_VTN keytype value structure.
   *
   * @param[in]  val_vtn  KT_VTN value structure.
   * @param[in]  operation  Operation type.
   *
   * @retval  UPLL_RC_SUCCESS               validation succeeded.
   * @retval  UPLL_RC_ERR_CFG_SYNTAX        validation failed.
   */
  upll_rc_t ValidateVtnValue(val_vtn_t *vtn_val, uint32_t operation);

  /**
   * @Brief  Validates the syntax for KT_VTN Rename structure.
   *
   * @param[in]  val_vtn  KT_VTN Rename structure.
   *
   * @retval  UPLL_RC_SUCCESS         validation succeeded.
   * @retval  UPLL_RC_ERR_CFG_SYNTAX  validation failed.
   */
  upll_rc_t ValidateVtnRenameValue(val_rename_vtn *vtn_rename);

  /**
   * @Brief  Validates the syntax of the specified key and value structure
   *        for KT_VTNSTATION_CONTROLLER keytype
   *
   * @param[in]  req    This structure contains IpcReqRespHeader
   *                    (first 8 fields of input request structure).
   * @param[in]  ikey   ikey contains key and value structure.
   *
   * @retval  UPLL_RC_SUCCESS               Successful.
   * @retval  UPLL_RC_ERR_CFG_SYNTAX        Syntax error.
   * @retval  UPLL_RC_ERR_NO_SUCH_INSTANCE  key_vtnstation_controller
   *                                        is not available.
   * @retval  UPLL_RC_ERR_GENERIC           Generic failure.
   * @retval  UPLL_RC_ERR_INVALID_OPTION1   option1 is not valid.
   * @retval  UPLL_RC_ERR_INVALID_OPTION2   option2 is not valid.
   **/
  upll_rc_t ValidateMessageForVtnStnCtrlr(IpcReqRespHeader *req,
                                          ConfigKeyVal *ikey);

  /**
   * @Brief  Validates the syntax of the specified key and value structure
   *         for KT_VTN_MAPPING_CONTROLLER keytype
   *
   * @param[in]  req    This structure contains IpcReqRespHeader
   *                    (first 8 fields of input request structure).
   * @param[in]  ikey   ikey contains key and value structure.
   *
   * @retval  UPLL_RC_SUCCESS               Successful.
   * @retval  UPLL_RC_ERR_CFG_SYNTAX        Syntax error.
   * @retval  UPLL_RC_ERR_NO_SUCH_INSTANCE  key_vtn_mapping_controller
   *                                        is not available.
   * @retval  UPLL_RC_ERR_GENERIC           Generic failure.
   * @retval  UPLL_RC_ERR_INVALID_OPTION1   option1 is not valid.
   * @retval  UPLL_RC_ERR_INVALID_OPTION2   option2 is not valid.
   **/
  upll_rc_t ValidateMessageForVtnMapCtrlr(IpcReqRespHeader *req,
                                          ConfigKeyVal *ikey);

  /*
   * @Brief  Validates the syntax for KT_VTNSTATION_CONTROLLER
   *         Keytype Key structure.
   *
   * @param[in]  val_vtn  KT_VTN key structure.
   * @param[in]  operation operation type.
   *
   * @retval  UPLL_RC_SUCCESS         validation succeeded.
   * @retval  UPLL_RC_ERR_CFG_SYNTAX  validation failed.
   *
   **/
  upll_rc_t ValidateVtnStnCtrlrKey(key_vtnstation_controller *vtn_ctrlr_key,
                                   unc_keytype_operation_t operation);

  /*
   * @Brief  Validates the syntax for KT_VTN_MAPPING_CONTROLLER Keytype Key structure.
   *
   * @param[in]  val_vtn  KT_VTN_MAPPING_CONTROLLER key structure.
   * @param[in]  operation Operation type.
   *
   * @retval  UPLL_RC_SUCCESS         validation succeeded.
   * @retval  UPLL_RC_ERR_CFG_SYNTAX  validation failed.
   *
   **/
  upll_rc_t ValidateVtnMapCtrlrKey(key_vtn_controller *vtn_ctrlr_key,
                                   unc_keytype_operation_t operation);

 protected:
  /* @brief         This method invoke when the VTN merge hapeening between
   *                Running and DT import. This will checks the vnode name
   *                unique or not and semantic checks like IP Address, Mac
   *                Address and network host address.
   *
   * @param[in]     keytype       UNC KEY TYPE
   * @param[in/out] ctrlr_id      Controller ID
   * @param[in]     conflict_ckv  key and value structure
   * @param[in]     dal    Pointer to the DalDmlIntf(DB Interface)
   *
   * @retval  UPLL_RC_SUCCESS                    Completed successfully.
   * @retval  UPLL_RC_ERR_GENERIC                Generic failure.
   * @retval  UPLL_RC_ERR_RESOURCE_DISCONNECTED  Resource disconnected.
   * @retval  UPLL_RC_ERR_DB_ACCESS              DB Read/Write error.
   * @retval  UPLL_RC_ERR_MERGE_CONFLICT         Semantic check error.
   * @retval  UPLL_RC_ERR_NO_SUCH_INSTANCE       Given key does not exist
   *
   **/
  upll_rc_t MergeValidateChildren(ConfigKeyVal *import_ckval,
                                  const char *ctrlr_id,
                                  ConfigKeyVal *ikey,
                                  DalDmlIntf *dmi);

  upll_rc_t SetCtrlrOperStatus(ConfigKeyVal *ikey,
                               state_notification notification,
                               DalDmlIntf *dmi, bool &oper_change);

  upll_rc_t SetOperStatus(ConfigKeyVal *ikey,
                          state_notification notification,
                          DalDmlIntf *dmi);
  upll_rc_t DupConfigKeyValVtnStation(ConfigKeyVal *&okey,
                                      ConfigKeyVal *req);
  upll_rc_t DupConfigKeyValVtnMapping(ConfigKeyVal *&okey,
                                      ConfigKeyVal *req);

 public:
  VtnMoMgr();
  virtual ~VtnMoMgr() {
    for (int i = 0; i < ntable; i++)
      if (table[i]) {
        delete table[i];
      }
    delete[] table;
  }
  /**
   * @brief Set vtn oper status
   *
   * @param[in]   vtn_name_o     Vtn name
   * @param[in]   dmi            Database connection parameter
   * @param[in]   notification   notification causing change in oper status
   * @param[out]  bool           true oper status set successfully
   */
  bool VtnSetOperStatus(uint8_t *vtn_name_o,
                        DalDmlIntf *dmi,
                        state_notification notification);


  upll_rc_t UpdateOperStatus(ConfigKeyVal *ck_vtn,
                             DalDmlIntf *dmi,
                             state_notification notification,
                             bool skip);
  /**
   * @brief Exposed to Physical to Verify whether particular Key
   *                            is in Use in Logical
   *
   * @param[in/out]   operation      Operation to check if the Controller is referenced
   *                                            in UPLL configuration
   * @param[in]       dt_type        Data Type
   * @param[in]       key_type       KeyType of the key to be verified
   * @param[in]       ckv            ConfigKeyVal of the KeyType
   * @param[out]      in_use         true if key is in use, else false
   * @param[out]      upll_rc_t      UPLL_RC_ERR_CFG_SEMANTIC on error
   *                                    UPLL_RC_SUCCESS on success
   *
   */
  upll_rc_t IsKeyInUse(upll_keytype_datatype_t dt_type,
                       const ConfigKeyVal *ckv, bool *in_use,
                       DalDmlIntf *dmi);
  /**
   * @brief      Method to get a configkeyval of a specified keytype from an input configkeyval
   *
   * @param[in/out]  okey                 pointer to output ConfigKeyVal
   * @param[in]      parent_key           pointer to the configkeyval from which the output configkey val is initialized.
   *
   * @retval         UPLL_RC_SUCCESS      Successfull completion.
   * @retval         UPLL_RC_ERR_GENERIC  Failure case.
   */
  upll_rc_t GetChildConfigKey(ConfigKeyVal *&okey, ConfigKeyVal *parent_key);
  upll_rc_t GetControllerDomainSpan(
      ConfigKeyVal *ikey,
      upll_keytype_datatype_t dt_type,
      DalDmlIntf *dmi,
      std::list<controller_domain_t> &list_ctrlr_dom);
  upll_rc_t GetControllerDomainSpan(ConfigKeyVal *ikey,
                                    upll_keytype_datatype_t dt_type,
                                    DalDmlIntf *dmi);
  upll_rc_t TxCopyCandidateToRunning(
      unc_key_type_t keytype, CtrlrCommitStatusList *ctrlr_commit_status,
      DalDmlIntf *dmi);
  upll_rc_t TxUpdateController(unc_key_type_t keytype, uint32_t session_id,
                               uint32_t config_id, uuc::UpdateCtrlrPhase phase,
                               set<string> *affected_ctrlr_set,
                               DalDmlIntf *dmi,
                               ConfigKeyVal **err_ckv);

  /* @brief         Set Consolidated config status when controller
   *                                        table entry is deleted
   *
   * @param[in]     ikey            Vtn Main Table Key
   * @param[in]     ctrlr_id        Controller Name which is deleted
   * @param[in]     dmi             Database object
   *
   */
  upll_rc_t SetVtnConsolidatedStatus(ConfigKeyVal *ikey, uint8_t *ctrlr_id,
                                     DalDmlIntf *dmi);
  /* @brief         Read the configuration either from RDBMS and/or from the
   *             controller
   *
   * @param[in]     req    Pointer to IpcResResHeader
   * @param[in/out] ikey   Pointer to the ConfigKeyVal Structure
   * @param[in]     dmi    Pointer to the DalDmlIntf(DB Interface)
   *
   * @retval  UPLL_RC_SUCCESS                    Completed successfully.
   * @retval  UPLL_RC_ERR_GENERIC                Generic failure.
   * @retval  UPLL_RC_ERR_RESOURCE_DISCONNECTED  Resource disconnected.
   * @retval  UPLL_RC_ERR_DB_ACCESS              DB Read/Write error.
   * @retval  UPLL_RC_ERR_NO_SUCH_INSTANCE       Given key does not exist
   *
   **/
  upll_rc_t ReadMo(IpcReqRespHeader *req, ConfigKeyVal *ikey,
                   DalDmlIntf *dmi);

  /* @brief         READ_SIBLING_BEGIN: Gets the first MO from the sibling group under the parent
   *                specified in the key from the specified UNC database
   *                READ_SIBLING: Gets the next MO from the sibling group under the parent
   *                specified in the key from the specified UNC database
   *
   * @param[in]     req    Pointer to IpcResResHeader
   * @param[in/out] key    Pointer to the ConfigKeyVal Structure
   * @param[in]     begin  boolean variable to decide the sibling operation
   * @param[in]     dal    Pointer to the DalDmlIntf(DB Interface)
   *
   * @retval  UPLL_RC_SUCCESS                    Completed successfully.
   * @retval  UPLL_RC_ERR_GENERIC                Generic failure.
   * @retval  UPLL_RC_ERR_RESOURCE_DISCONNECTED  Resource disconnected.
   * @retval  UPLL_RC_ERR_DB_ACCESS              DB Read/Write error.
   * @retval  UPLL_RC_ERR_NO_SUCH_INSTANCE       Given key does not exist
   *
   **/
  upll_rc_t ReadSiblingMo(IpcReqRespHeader *req, ConfigKeyVal *ikey,
                          bool begin, DalDmlIntf *dmi);

  /* @brief         This method invoke when the VTN merge hapeening between
   *                Running and DT import. This will checks the vnode name
   *                unique or not and semantic checks like IP Address, Mac
   *                Address and network host address.
   *
   * @param[in]     keytype       UNC KEY TYPE
   * @param[in/out] ctrlr_id      Controller ID
   * @param[in]     conflict_ckv  key and value structure
   * @param[in]     dal    Pointer to the DalDmlIntf(DB Interface)
   *
   * @retval  UPLL_RC_SUCCESS                    Completed successfully.
   * @retval  UPLL_RC_ERR_GENERIC                Generic failure.
   * @retval  UPLL_RC_ERR_RESOURCE_DISCONNECTED  Resource disconnected.
   * @retval  UPLL_RC_ERR_DB_ACCESS              DB Read/Write error.
   * @retval  UPLL_RC_ERR_MERGE_CONFLICT         Semantic check error.
   * @retval  UPLL_RC_ERR_NO_SUCH_INSTANCE       Given key does not exist
   *
   **/
  upll_rc_t ReadSiblingCount(IpcReqRespHeader *req, ConfigKeyVal *ikey,
                             DalDmlIntf *dmi);

  upll_rc_t MergeValidate(unc_key_type_t keytype, const char *ctrlr_id,
                          ConfigKeyVal *conflict_ckv, DalDmlIntf *dmi);
  /* Rename */

  /**
   * @brief     Method create configkey for the VTN.
   *            Allocates the memory for the okey and
   *            Copy the old name from the rename_info into okey.
   *
   * @param[in]  okey                        key and value structure.
   * @param[in]  rename_info                 key and value structure.
   *
   * @retval     UPLL_RC_SUCCESS             Successfull completion.
   * @retval     UPLL_RC_ERR_GENERIC         Failure case.
   */
  upll_rc_t CopyToConfigKey(ConfigKeyVal *&okey, ConfigKeyVal *ikey);

  /**
   * @brief     Method used in Rename Operation.
   *            This function collects the Unc new name from okey, Unc old name and
   *            Ctrlr name  from the ikey and creats the rename_info
   *            ConfigKeyVal
   *
   * @param[in]  ikey                        key and value structure.
   * @param[in]  okey                        key and value structure.
   * @param[out] rename_info                key and value structure.
   * @param[in]  dmi                         Pointer to DalDmlIntf Class.
   * @param[in]  ctrlr_id                    Controller Id.
   * @param[in]  renamed                     Flag for Already renamed or not.
   *
   * @retval     UPLL_RC_SUCCESS             Successfull completion.
   * @retval     UPLL_RC_ERR_GENERIC         Failure case.
   */
  upll_rc_t GetRenameInfo(ConfigKeyVal *ikey, ConfigKeyVal *okey,
                          ConfigKeyVal *&rename_info, DalDmlIntf *dmi,
                          const char *ctrlr_id, bool &renamed);

  /**
   * @brief     Method used in rename opertaion while update the new name into the tables
   *            to Gets the bindinfo detail for the VTN key.
   *
   * @param[in]  key_type                    unc key type.
   * @param[out] binfo                       Bindinfo details.
   * @param[out] nattr                       Number of Attributes.
   * @param[in]  tbl                         Table Name.
   *
   * @retval     PFC_TRUE                    Successfull completion.
   */
  bool GetRenameKeyBindInfo(unc_key_type_t key_type, BindInfo *&binfo,
                            int &nattr, MoMgrTables tbl);
  /*
   * @Brief Validates the syntax for KT_VTN Keytype Key structure.
   *
   * @param[in]  val_vtn  KT_VTN key structure.
   *
   * @retval  UPLL_RC_SUCCESS         validation succeeded.
   * @retval  UPLL_RC_ERR_CFG_SYNTAX  validation failed.
   *
   */
  upll_rc_t ValidateVtnKey(key_vtn_t *vtn_key);

  /* @brief     To convert the value structure read from DB to
   *            VTNService during READ operations
   * @param[in/out] ikey      Pointer to the ConfigKeyVal Structure
   *
   * @retval  UPLL_RC_SUCCESS                    Completed successfully.
   * @retval  UPLL_RC_ERR_GENERIC                Generic failure.
   *
   **/
  upll_rc_t AdaptValToVtnService(ConfigKeyVal *ikey);

  upll_rc_t SetConsolidatedStatus(ConfigKeyVal *ikey, DalDmlIntf *dmi);

  /* @brief     To update oper status of vnode
   *
   * @param[in] ktype         keytype
   * @param[in] session_id    session identifier
   * @param[in] config_id     config identifier
   * @param[in] dmi           Pointer to db connection instance
   *
   * @retval  UPLL_RC_SUCCESS                    updated successfully.
   * @retval  UPLL_RC_ERR_GENERIC                Generic failure.
   * @retval  UPLL_RC_ERR_DB_ACCESS              DB Read/Write error.
   *
   **/

  upll_rc_t TxUpdateDtState(unc_key_type_t ktype,
                            uint32_t session_id,
                            uint32_t config_id,
                            DalDmlIntf *dmi);
  upll_rc_t ControllerStatusHandler(uint8_t *ctrlr_name,
                                    DalDmlIntf *dmi,
                                    bool operstatus);
  upll_rc_t UpdateVnodeIfOperStatus(ConfigKeyVal *ck_vtn,
                                    DalDmlIntf *dmi,
                                    state_notification notification,
                                    bool skip,
                                    int if_type);
  upll_rc_t UpdateVnodeOperStatus(uint8_t *ctrlr_name,
                                  DalDmlIntf *dmi,
                                  state_notification notification,
                                  bool skip);
  upll_rc_t RestoreVtnOperStatus(ConfigKeyVal *ck_vtn,
                                 DalDmlIntf *dmi,
                                 state_notification notification);
  upll_rc_t RestoreVtnCtrlrOperStatus(uint8_t *ctrlr_name,
                                      DalDmlIntf *dmi,
                                      state_notification notification);
  upll_rc_t GetRenamedControllerKey(ConfigKeyVal *ikey,
                                    upll_keytype_datatype_t dt_type,
                                    DalDmlIntf *dmi,
                                    controller_domain *ctrlr_dom);
  upll_rc_t GetRenamedUncKey(ConfigKeyVal *ctrlr_key,
                             upll_keytype_datatype_t dt_type, DalDmlIntf *dmi,
                             uint8_t *ctrlr_id);
  upll_rc_t RestoreVnodeOperStatus(uint8_t *ctrlr_id,
                                   ConfigKeyVal *ck_vtn,
                                   DalDmlIntf *dmi,
                                   state_notification notification,
                                   bool skip);
  upll_rc_t RestoreVnodeIfAndVtnCtrlr(uint8_t *ctrlr_id,
                                      ConfigKeyVal *ck_vtn,
                                      DalDmlIntf *dmi,
                                      state_notification notification,
                                      uint32_t &down_count);
  upll_rc_t VtnRenameMerge(ConfigKeyVal *ikey,
                           ConfigKeyVal *okey,
                           DalDmlIntf *dmi);
  upll_rc_t MergeVtnMainTable(DalDmlIntf *dmi);
  upll_rc_t GetVbridgeUnKnownInstance(ConfigKeyVal *ck_vtn,
                                      uint8_t *ctrlr_id,
                                      DalDmlIntf *dmi);
  upll_rc_t UpdateVbridgeIfUnKnownInstance(ConfigKeyVal *ck_vnode,
                                           uint8_t *ctrlr_id,
                                           DalDmlIntf *dmi);
};

typedef struct val_db_vtn_st {
  val_vtn_st vtn_val_st;
  uint32_t    down_count;
} val_db_vtn_st_t;

typedef struct val_vtn_ctrlr {
  uint8_t valid[2];
  val_oper_status oper_status;
  val_alarm_status alarm_status;
  unc_keytype_configstatus_t cs_attr[1];
  unc_keytype_configstatus_t cs_row_status;
  uint32_t down_count;
  uint32_t ref_count;
  uint8_t flags;
} val_vtn_ctrlr_t;
}  // namespace kt_momgr
}  // namespace upll
}  // namespace unc
#endif

