//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, v2.2.11 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2015.02.16 at 08:18:45 PM EET 
//


package fi.eonwe.wikilinks;

import java.math.BigInteger;
import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlSchemaType;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for DiscussionThreadingInfo complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="DiscussionThreadingInfo"&gt;
 *   &lt;complexContent&gt;
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType"&gt;
 *       &lt;sequence&gt;
 *         &lt;element name="ThreadSubject" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="ThreadParent" type="{http://www.w3.org/2001/XMLSchema}positiveInteger"/&gt;
 *         &lt;element name="ThreadAncestor" type="{http://www.w3.org/2001/XMLSchema}positiveInteger"/&gt;
 *         &lt;element name="ThreadPage" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="ThreadID" type="{http://www.w3.org/2001/XMLSchema}positiveInteger"/&gt;
 *         &lt;element name="ThreadAuthor" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="ThreadEditStatus" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *         &lt;element name="ThreadType" type="{http://www.w3.org/2001/XMLSchema}string"/&gt;
 *       &lt;/sequence&gt;
 *     &lt;/restriction&gt;
 *   &lt;/complexContent&gt;
 * &lt;/complexType&gt;
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "DiscussionThreadingInfo", propOrder = {
    "threadSubject",
    "threadParent",
    "threadAncestor",
    "threadPage",
    "threadID",
    "threadAuthor",
    "threadEditStatus",
    "threadType"
})
@Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
public class DiscussionThreadingInfo {

    @XmlElement(name = "ThreadSubject", required = true)
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    protected String threadSubject;
    @XmlElement(name = "ThreadParent", required = true)
    @XmlSchemaType(name = "positiveInteger")
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    protected BigInteger threadParent;
    @XmlElement(name = "ThreadAncestor", required = true)
    @XmlSchemaType(name = "positiveInteger")
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    protected BigInteger threadAncestor;
    @XmlElement(name = "ThreadPage", required = true)
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    protected String threadPage;
    @XmlElement(name = "ThreadID", required = true)
    @XmlSchemaType(name = "positiveInteger")
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    protected BigInteger threadID;
    @XmlElement(name = "ThreadAuthor", required = true)
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    protected String threadAuthor;
    @XmlElement(name = "ThreadEditStatus", required = true)
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    protected String threadEditStatus;
    @XmlElement(name = "ThreadType", required = true)
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    protected String threadType;

    /**
     * Gets the value of the threadSubject property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public String getThreadSubject() {
        return threadSubject;
    }

    /**
     * Sets the value of the threadSubject property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public void setThreadSubject(String value) {
        this.threadSubject = value;
    }

    /**
     * Gets the value of the threadParent property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public BigInteger getThreadParent() {
        return threadParent;
    }

    /**
     * Sets the value of the threadParent property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public void setThreadParent(BigInteger value) {
        this.threadParent = value;
    }

    /**
     * Gets the value of the threadAncestor property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public BigInteger getThreadAncestor() {
        return threadAncestor;
    }

    /**
     * Sets the value of the threadAncestor property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public void setThreadAncestor(BigInteger value) {
        this.threadAncestor = value;
    }

    /**
     * Gets the value of the threadPage property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public String getThreadPage() {
        return threadPage;
    }

    /**
     * Sets the value of the threadPage property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public void setThreadPage(String value) {
        this.threadPage = value;
    }

    /**
     * Gets the value of the threadID property.
     * 
     * @return
     *     possible object is
     *     {@link BigInteger }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public BigInteger getThreadID() {
        return threadID;
    }

    /**
     * Sets the value of the threadID property.
     * 
     * @param value
     *     allowed object is
     *     {@link BigInteger }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public void setThreadID(BigInteger value) {
        this.threadID = value;
    }

    /**
     * Gets the value of the threadAuthor property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public String getThreadAuthor() {
        return threadAuthor;
    }

    /**
     * Sets the value of the threadAuthor property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public void setThreadAuthor(String value) {
        this.threadAuthor = value;
    }

    /**
     * Gets the value of the threadEditStatus property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public String getThreadEditStatus() {
        return threadEditStatus;
    }

    /**
     * Sets the value of the threadEditStatus property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public void setThreadEditStatus(String value) {
        this.threadEditStatus = value;
    }

    /**
     * Gets the value of the threadType property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public String getThreadType() {
        return threadType;
    }

    /**
     * Sets the value of the threadType property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.sun.tools.xjc.Driver", date = "2015-02-16T08:18:45+02:00", comments = "JAXB RI v2.2.11")
    public void setThreadType(String value) {
        this.threadType = value;
    }

}
