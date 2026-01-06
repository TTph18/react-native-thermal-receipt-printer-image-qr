import { NativeEventEmitter } from "react-native";
import { COMMANDS } from "./utils/printer-commands";
export interface PrinterOptions {
    beep?: boolean;
    cut?: boolean;
    tailingLine?: boolean;
    encoding?: string;
}
export declare enum PrinterWidth {
    "58mm" = 58,
    "80mm" = 80
}
export interface PrinterImageOptions {
    beep?: boolean;
    cut?: boolean;
    tailingLine?: boolean;
    encoding?: string;
    imageWidth?: number;
    imageHeight?: number;
    printerWidthType?: PrinterWidth;
    paddingX?: number;
}
export interface IUSBPrinter {
    device_name: string;
    vendor_id: string;
    product_id: string;
}
export interface IBLEPrinter {
    device_name: string;
    inner_mac_address: string;
}
export interface INetPrinter {
    host: string;
    port: number;
}
export declare enum ColumnAlignment {
    LEFT = 0,
    CENTER = 1,
    RIGHT = 2
}
declare const USBPrinter: {
    init: () => Promise<void>;
    getDeviceList: () => Promise<IUSBPrinter[]>;
    connectPrinter: (vendorId: string, productId: string) => Promise<IUSBPrinter>;
    closeConn: () => Promise<void>;
    printText: (text: string, opts?: PrinterOptions) => void;
    printBill: (text: string, opts?: PrinterOptions) => void;
    /**
     * image url
     * @param imgUrl
     * @param opts
     */
    printImage: (imgUrl: string, opts?: PrinterImageOptions) => void;
    /**
     * base 64 string
     * @param Base64
     * @param opts
     */
    printImageBase64: (Base64: string, opts?: PrinterImageOptions) => void;
    /**
     * Print raw data (base64 encoded)
     * For ESC/POS printers: Use printText() or printBill() instead
     * For TSPL printers: Use printTSPL() instead
     * @param text Base64-encoded raw data
     */
    printRaw: (text: string) => void;
    /**
     * Print TSPL (TSC Printer Language) commands for label printers
     * @param base64TSPLCommands Base64-encoded TSPL command string
     * Example TSPL commands:
     *   "SIZE 35 mm, 22 mm\r\nGAP 2 mm, 0 mm\r\nCLS\r\nBITMAP 0,0,280,176,2,<data>\r\nPRINT 1,1\r\n"
     * Convert to base64 before passing to this method.
     */
    printTSPL: (base64TSPLCommands: string) => void;
    /**
     * Generate TSPL command for simple text label (generated on Kotlin side)
     * @param width Label width in mm
     * @param height Label height in mm
     * @param gap Gap between labels in mm
     * @param text Text to print
     * @param x X position
     * @param y Y position
     * @param fontSize Font size (1-8)
     * @returns Promise<string> Base64-encoded TSPL commands
     */
    generateTSPLTextLabel: (width: number, height: number, gap: number, text: string, x: number, y: number, fontSize: number) => Promise<string>;
    /**
     * Encode TSPL command string to base64 (generated on Kotlin side)
     * Use this if you're generating TSPL commands in Kotlin and just need to encode them
     * @param tsplCommand TSPL command string
     * @returns Promise<string> Base64-encoded TSPL commands
     */
    encodeTSPLCommand: (tsplCommand: string) => Promise<string>;
    /**
     * Generate TSPL and print directly (all on Kotlin side)
     * Convenience method that generates TSPL command and prints it in one call
     * @param width Label width in mm
     * @param height Label height in mm
     * @param gap Gap between labels in mm
     * @param text Text to print
     * @param x X position
     * @param y Y position
     * @param fontSize Font size (1-8)
     */
    printTSPLTextLabel: (width: number, height: number, gap: number, text: string, x: number, y: number, fontSize: number) => void;
    /**
     * Generate TSPL label with image from base64 image data (generated on Kotlin side)
     * Image dimensions are automatically detected. Label size is auto-calculated if width/height are 0.
     * @param base64Image Base64-encoded image data
     * @param labelWidth Label width in mm (0 = auto-calculate from image)
     * @param labelHeight Label height in mm (0 = auto-calculate from image)
     * @param gap Gap between labels in mm
     * @param x X position for image
     * @param y Y position for image
     * @returns Promise<string> Base64-encoded TSPL commands
     */
    generateTSPLImageLabel: (base64Image: string, labelWidth: number, labelHeight: number, gap: number, x: number, y: number) => Promise<string>;
    /**
     * Generate TSPL label with image from image URL (generated on Kotlin side)
     * Image dimensions are automatically detected. Label size is auto-calculated if width/height are 0.
     * @param imageUrl URL of the image
     * @param labelWidth Label width in mm (0 = auto-calculate from image)
     * @param labelHeight Label height in mm (0 = auto-calculate from image)
     * @param gap Gap between labels in mm
     * @param x X position for image
     * @param y Y position for image
     * @returns Promise<string> Base64-encoded TSPL commands
     */
    generateTSPLImageLabelFromURL: (imageUrl: string, labelWidth: number, labelHeight: number, gap: number, x: number, y: number) => Promise<string>;
    /**
     * Generate TSPL label with image and text, then print directly (all on Kotlin side)
     * Image dimensions are automatically detected. Label size is auto-calculated if width/height are 0.
     * @param base64Image Base64-encoded image data (can be null/empty)
     * @param labelWidth Label width in mm (0 = auto-calculate from image)
     * @param labelHeight Label height in mm (0 = auto-calculate from image)
     * @param gap Gap between labels in mm
     * @param imageX X position for image
     * @param imageY Y position for image
     * @param text Text to print (can be null/empty)
     * @param textX X position for text
     * @param textY Y position for text
     * @param fontSize Font size (1-8)
     */
    printTSPLImageLabel: (base64Image: string | null, labelWidth: number, labelHeight: number, gap: number, imageX: number, imageY: number, text: string | null, textX: number, textY: number, fontSize: number) => void;
    /**
     * `columnWidth`
     * 80mm => 46 character
     * 58mm => 30 character
     */
    printColumnsText: (texts: string[], columnWidth: number[], columnAlignment: ColumnAlignment[], columnStyle: string[], opts?: PrinterOptions) => void;
};
declare const BLEPrinter: {
    init: () => Promise<void>;
    getDeviceList: () => Promise<IBLEPrinter[]>;
    connectPrinter: (inner_mac_address: string) => Promise<IBLEPrinter>;
    closeConn: () => Promise<void>;
    printText: (text: string, opts?: PrinterOptions) => void;
    printBill: (text: string, opts?: PrinterOptions) => void;
    /**
     * image url
     * @param imgUrl
     * @param opts
     */
    printImage: (imgUrl: string, opts?: PrinterImageOptions) => void;
    /**
     * base 64 string
     * @param Base64
     * @param opts
     */
    printImageBase64: (Base64: string, opts?: PrinterImageOptions) => void;
    /**
     * android print with encoder
     * @param text
     */
    printRaw: (text: string) => void;
    /**
     * `columnWidth`
     * 80mm => 46 character
     * 58mm => 30 character
     */
    printColumnsText: (texts: string[], columnWidth: number[], columnAlignment: ColumnAlignment[], columnStyle: string[], opts?: PrinterOptions) => void;
};
declare const NetPrinter: {
    init: () => Promise<void>;
    getDeviceList: () => Promise<INetPrinter[]>;
    connectPrinter: (host: string, port: number, timeout?: number) => Promise<INetPrinter>;
    closeConn: () => Promise<void>;
    printText: (text: string, opts?: {}) => void;
    printBill: (text: string, opts?: PrinterOptions) => void;
    /**
     * image url
     * @param imgUrl
     * @param opts
     */
    printImage: (imgUrl: string, opts?: PrinterImageOptions) => void;
    /**
     * base 64 string
     * @param Base64
     * @param opts
     */
    printImageBase64: (Base64: string, opts?: PrinterImageOptions) => void;
    /**
     * Android print with encoder
     * @param text
     */
    printRaw: (text: string) => void;
    /**
     * `columnWidth`
     * 80mm => 46 character
     * 58mm => 30 character
     */
    printColumnsText: (texts: string[], columnWidth: number[], columnAlignment: ColumnAlignment[], columnStyle?: string[], opts?: PrinterOptions) => void;
};
declare const NetPrinterEventEmitter: NativeEventEmitter;
export { COMMANDS, NetPrinter, BLEPrinter, USBPrinter, NetPrinterEventEmitter };
export declare enum RN_THERMAL_RECEIPT_PRINTER_EVENTS {
    EVENT_NET_PRINTER_SCANNED_SUCCESS = "scannerResolved",
    EVENT_NET_PRINTER_SCANNING = "scannerRunning",
    EVENT_NET_PRINTER_SCANNED_ERROR = "registerError"
}
