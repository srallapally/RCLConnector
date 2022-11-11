package org.example;

public class IDMRole {
    private String _roleId;
    private String _roleRef;
    private String _roleRev;

    private String _roleName;
    private String _refPropId;
    private String _refPropRev;
    private String _refResourceId;
    private String _refResourceRev;
    public IDMRole(String roleId, String roleRev, String refResourceId,String refResourceRev, String roleName, String roleRef,String refPropId,String refPropRev ) {
        _roleId = roleId;
        _roleRef = roleRef;
        _roleRev = roleRev;
        _refPropId = refPropId;
        _refPropRev = refPropRev;
        _refResourceId = refResourceId;
        _refResourceRev = refResourceRev;
        _roleName = roleName;
    }

    public String get_roleId() {
        return _roleId;
    }

    public void set_roleId(String _roleId) {
        this._roleId = _roleId;
    }

    public String get_roleRef() {
        return _roleRef;
    }

    public void set_roleRef(String _roleRef) {
        this._roleRef = _roleRef;
    }

    public String get_roleRev() {
        return _roleRev;
    }

    public void set_roleRev(String _roleRev) {
        this._roleRev = _roleRev;
    }

    public String get_refPropId() {
        return _refPropId;
    }

    public void set_refPropId(String _refPropId) {
        this._refPropId = _refPropId;
    }

    public String get_refPropRev() {
        return _refPropRev;
    }

    public void set_refPropRev(String _refPropRev) {
        this._refPropRev = _refPropRev;
    }

    public String get_refResourceId() {
        return _refResourceId;
    }

    public void set_refResourceId(String _refResourceId) {
        this._refResourceId = _refResourceId;
    }

    public String get_refResourceRev() {
        return _refResourceRev;
    }

    public void set_refResourceRev(String _refResourceRev) {
        this._refResourceRev = _refResourceRev;
    }

    public String get_roleName() {
        return _roleName;
    }

    public void set_roleName(String _roleName) {
        this._roleName = _roleName;
    }
}
