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
export interface TSPLImageLabelOptions {
    gapMM?: number;
    dotMM?: number;
    printerWidthMM: number;
    printerHeightMM: number;
    left?: number;
    top?: number;
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
     * Print TSPL label with image, then print directly
     * @param base64Image Base64-encoded image data
     * @param options Configuration options for TSPL image label printing
     */
    printTSPLImageLabel: (base64Image: string | null, options: TSPLImageLabelOptions) => void;
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
