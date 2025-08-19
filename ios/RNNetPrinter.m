//
//  RNNetPrinter.m
//  RNThermalReceiptPrinter
//
//  Created by MTT on 06/11/19.
//  Copyright © 2019 Facebook. All rights reserved.
//


#import "RNNetPrinter.h"
#import "PrinterSDK.h"
#include <ifaddrs.h>
#include <arpa/inet.h>
#import <Foundation/Foundation.h>
#import <UIKit/UIKit.h>

NSString *const EVENT_SCANNER_RESOLVED = @"scannerResolved";
NSString *const EVENT_SCANNER_RUNNING = @"scannerRunning";

@interface PrivateIP : NSObject

@end

@implementation PrivateIP

- (NSString *)getIPAddress {

    NSString *address = @"error";
    struct ifaddrs *interfaces = NULL;
    struct ifaddrs *temp_addr = NULL;
    int success = 0;
    // retrieve the current interfaces - returns 0 on success
    success = getifaddrs(&interfaces);
    if (success == 0) {
        // Loop through linked list of interfaces
        temp_addr = interfaces;
        while(temp_addr != NULL) {
            if(temp_addr->ifa_addr->sa_family == AF_INET) {
                // Check if interface is en0 which is the wifi connection on the iPhone
                if([[NSString stringWithUTF8String:temp_addr->ifa_name] isEqualToString:@"en0"]) {
                    // Get NSString from C String
                    address = [NSString stringWithUTF8String:inet_ntoa(((struct sockaddr_in *)temp_addr->ifa_addr)->sin_addr)];

                }

            }

            temp_addr = temp_addr->ifa_next;
        }
    }
    // Free memory
    freeifaddrs(interfaces);
    return address;

}

@end

@implementation RNNetPrinter

- (dispatch_queue_t)methodQueue
{
    return dispatch_get_main_queue();
}
RCT_EXPORT_MODULE()

- (NSArray<NSString *> *)supportedEvents
{
    return @[EVENT_SCANNER_RESOLVED, EVENT_SCANNER_RUNNING];
}

RCT_EXPORT_METHOD(init:(RCTResponseSenderBlock)successCallback
                  fail:(RCTResponseSenderBlock)errorCallback) {
    connected_ip = nil;
    is_scanning = NO;
    _printerArray = [NSMutableArray new];
    successCallback(@[@"Init successful"]);
}

RCT_EXPORT_METHOD(getDeviceList:(RCTResponseSenderBlock)successCallback
                  fail:(RCTResponseSenderBlock)errorCallback) {
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handlePrinterConnectedNotification:) name:PrinterConnectedNotification object:nil];
    [[NSNotificationCenter defaultCenter] addObserver:self selector:@selector(handleBLEPrinterConnectedNotification:) name:@"BLEPrinterConnected" object:nil];
    dispatch_async(dispatch_get_global_queue(DISPATCH_QUEUE_PRIORITY_DEFAULT, 0), ^{
        [self scan:successCallback];
    });
}

- (void) scan: (RCTResponseSenderBlock)successCallback {
    @try {
        PrivateIP *privateIP = [[PrivateIP alloc]init];
        NSString *localIP = [privateIP getIPAddress];
        is_scanning = YES;
        [self sendEventWithName:EVENT_SCANNER_RUNNING body:@YES];
        _printerArray = [NSMutableArray new];

        NSString *prefix = [localIP substringToIndex:([localIP rangeOfString:@"." options:NSBackwardsSearch].location)];
        NSInteger suffix = [[localIP substringFromIndex:([localIP rangeOfString:@"." options:NSBackwardsSearch].location)] intValue];

        for (NSInteger i = 1; i < 255; i++) {
            if (i == suffix) continue;
            NSString *testIP = [NSString stringWithFormat:@"%@.%ld", prefix, (long)i];
            current_scan_ip = testIP;
            [[PrinterSDK defaultPrinterSDK] connectIP:testIP];
            [NSThread sleepForTimeInterval:0.5];
        }

        NSOrderedSet *orderedSet = [NSOrderedSet orderedSetWithArray:_printerArray];
        NSArray *arrayWithoutDuplicates = [orderedSet array];
        _printerArray = (NSMutableArray *)arrayWithoutDuplicates;

        [self sendEventWithName:EVENT_SCANNER_RESOLVED body:_printerArray];

        successCallback(@[_printerArray]);
    } @catch (NSException *exception) {
        NSLog(@"No connection");
    }
    [[PrinterSDK defaultPrinterSDK] disconnect];
    is_scanning = NO;
    [self sendEventWithName:EVENT_SCANNER_RUNNING body:@NO];
}

- (void)handlePrinterConnectedNotification:(NSNotification*)notification
{
    if (is_scanning) {
        [_printerArray addObject:@{@"host": current_scan_ip, @"port": @9100}];
    }
}

- (void)handleBLEPrinterConnectedNotification:(NSNotification*)notification
{
    connected_ip = nil;
}

RCT_EXPORT_METHOD(connectPrinter:(NSString *)host
                  withPort:(nonnull NSNumber *)port
                  success:(RCTResponseSenderBlock)successCallback
                  fail:(RCTResponseSenderBlock)errorCallback) {
    @try {
        BOOL isConnectSuccess = [[PrinterSDK defaultPrinterSDK] connectIP:host];
        !isConnectSuccess ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer %@", host] : nil;

        connected_ip = host;
        [[NSNotificationCenter defaultCenter] postNotificationName:@"NetPrinterConnected" object:nil];
        successCallback(@[[NSString stringWithFormat:@"Connecting to printer %@", host]]);

    } @catch (NSException *exception) {
        errorCallback(@[exception.reason]);
    }
}

RCT_EXPORT_METHOD(printRawData:(NSString *)text
                  printerOptions:(NSDictionary *)options
                  fail:(RCTResponseSenderBlock)errorCallback) {
    @try {
        NSNumber* beepPtr = [options valueForKey:@"beep"];
        NSNumber* cutPtr = [options valueForKey:@"cut"];

        BOOL beep = (BOOL)[beepPtr intValue];
        BOOL cut = (BOOL)[cutPtr intValue];

        !connected_ip ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer"] : nil;

        // [[PrinterSDK defaultPrinterSDK] printTestPaper];
        [[PrinterSDK defaultPrinterSDK] printText:text];
        beep ? [[PrinterSDK defaultPrinterSDK] beep] : nil;
        cut ? [[PrinterSDK defaultPrinterSDK] cutPaper] : nil;
    } @catch (NSException *exception) {
        errorCallback(@[exception.reason]);
    }
}

RCT_EXPORT_METHOD(printImageData:(NSString *)imgUrl
                  printerOptions:(NSDictionary *)options
                  fail:(RCTResponseSenderBlock)errorCallback) {
    @try {

        !connected_ip ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer"] : nil;
        NSURL* url = [NSURL URLWithString:imgUrl];
        NSData* imageData = [NSData dataWithContentsOfURL:url];

        NSString* printerWidthType = [options valueForKey:@"printerWidthType"];

        NSInteger printerWidth = 576;

        if(printerWidthType != nil && [printerWidthType isEqualToString:@"58"]) {
            printerWidth = 384;
        }

        if(imageData != nil){
            UIImage* image = [UIImage imageWithData:imageData];
            UIImage* printImage = [self getPrintImage:image printerOptions:options];

            [[PrinterSDK defaultPrinterSDK] setPrintWidth:printerWidth];
            [[PrinterSDK defaultPrinterSDK] printImage:printImage ];
        }

    } @catch (NSException *exception) {
        errorCallback(@[exception.reason]);
    }
}

RCT_EXPORT_METHOD(printImageBase64:(NSString *)base64Qr
                  printerOptions:(NSDictionary *)options
                  fail:(RCTResponseSenderBlock)errorCallback) {
    @try {
        !connected_ip ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer"] : nil;
        
        if(![base64Qr isEqual: @""]){
            NSInteger nWidth = [[options valueForKey:@"width"] integerValue];
            NSString* printerWidthType = [options valueForKey:@"printerWidthType"];
            
            // Set default width based on printer type
            if (!nWidth) {
                if (printerWidthType != nil && [printerWidthType isEqualToString:@"58"]) {
                    nWidth = 384;
                } else {
                    nWidth = 576;
                }
            }
            
            NSInteger paddingLeft = [[options valueForKey:@"left"] integerValue];
            if (!paddingLeft) paddingLeft = 0;
            
            NSData *decoded = [[NSData alloc] initWithBase64EncodedString:base64Qr options:0];
            UIImage *srcImage = [[UIImage alloc] initWithData:decoded scale:1];
            NSData *jpgData = UIImageJPEGRepresentation(srcImage, 1);
            UIImage *jpgImage = [[UIImage alloc] initWithData:jpgData];
            
            NSInteger imgHeight = jpgImage.size.height;
            NSInteger imagWidth = jpgImage.size.width;
            NSInteger width = nWidth;
            CGSize size = CGSizeMake(width, imgHeight*width/imagWidth);
            UIImage *scaled = [RNNetPrinter imageWithImage:jpgImage scaledToFillSize:size];
            
            if (paddingLeft > 0) {
                scaled = [RNNetPrinter imagePadLeft:paddingLeft withSource:scaled];
                size = [scaled size];
            }
            
            unsigned char *graImage = [RNNetPrinter imageToGreyImage:scaled];
            unsigned char *formatedData = [RNNetPrinter format_K_threshold:graImage width:size.width height:size.height];
            NSData *dataToPrint = [RNNetPrinter eachLinePixToCmd:formatedData nWidth:size.width nHeight:size.height nMode:0];
            
            // Send data to printer
            [[PrinterSDK defaultPrinterSDK] setPrintWidth:width];
            
            // Convert NSData to hex string
            NSMutableString *hexString = [[NSMutableString alloc] init];
            const unsigned char *bytes = [dataToPrint bytes];
            for (NSUInteger i = 0; i < [dataToPrint length]; i++) {
                [hexString appendFormat:@"%02X", bytes[i]];
            }
            [[PrinterSDK defaultPrinterSDK] sendHex:hexString];
            
            // Clean up memory
            free(graImage);
            free(formatedData);
        }
    } @catch (NSException *exception) {
        errorCallback(@[exception.reason]);
    }
}

// Image processing methods based on react-native-bluetooth-escpos-printer

+ (UIImage *)imageWithImage:(UIImage *)image scaledToFillSize:(CGSize)size {
    CGFloat scale = MAX(size.width/image.size.width, size.height/image.size.height);
    CGFloat width = image.size.width * scale;
    CGFloat height = image.size.height * scale;
    CGRect imageRect = CGRectMake((size.width - width)/2.0f,
                                  (size.height - height)/2.0f,
                                  width,
                                  height);
    
    UIGraphicsBeginImageContextWithOptions(size, NO, 0);
    [image drawInRect:imageRect];
    UIImage *newImage = UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return newImage;
}

+ (UIImage*)imagePadLeft:(NSInteger) left withSource: (UIImage*)source {
    CGSize orgSize = [source size];
    CGSize size = CGSizeMake(orgSize.width + [[NSNumber numberWithInteger: left] floatValue], orgSize.height);
    UIGraphicsBeginImageContext(size);
    CGContextRef context = UIGraphicsGetCurrentContext();
    CGContextSetFillColorWithColor(context,
                                   [[UIColor whiteColor] CGColor]);
    CGContextFillRect(context, CGRectMake(0, 0, size.width, size.height));
    [source drawInRect:CGRectMake(left, 0, orgSize.width, orgSize.height)
             blendMode:kCGBlendModeNormal alpha:1.0];
    UIImage *paddedImage =  UIGraphicsGetImageFromCurrentImageContext();
    UIGraphicsEndImageContext();
    return paddedImage;
}

+ (uint8_t *)imageToGreyImage:(UIImage *)image {
    // Create image rectangle with current image width/height
    int kRed = 1;
    int kGreen = 2;
    int kBlue = 4;
    int colors = kGreen | kBlue | kRed;
    
    CGFloat actualWidth = image.size.width;
    CGFloat actualHeight = image.size.height;
    NSLog(@"Converting image to grayscale: %fx%f", actualWidth, actualHeight);
    
    uint32_t *rgbImage = (uint32_t *) malloc(actualWidth * actualHeight * sizeof(uint32_t));
    CGColorSpaceRef colorSpace = CGColorSpaceCreateDeviceRGB();
    CGContextRef context = CGBitmapContextCreate(rgbImage, actualWidth, actualHeight, 8, actualWidth*4, colorSpace,
                                                 kCGBitmapByteOrder32Little | kCGImageAlphaNoneSkipLast);
    CGContextSetInterpolationQuality(context, kCGInterpolationHigh);
    CGContextSetShouldAntialias(context, NO);
    CGContextDrawImage(context, CGRectMake(0, 0, actualWidth, actualHeight), [image CGImage]);
    CGContextRelease(context);
    CGColorSpaceRelease(colorSpace);
    
    // Improved grayscale conversion using standard luminance formula
    uint8_t *m_imageData = (uint8_t *) malloc(actualWidth * actualHeight);
    for(int y = 0; y < actualHeight; y++) {
        for(int x = 0; x < actualWidth; x++) {
            uint32_t rgbPixel = rgbImage[(int)(y*actualWidth+x)];
            uint8_t r = (rgbPixel >> 24) & 0xFF;
            uint8_t g = (rgbPixel >> 16) & 0xFF;
            uint8_t b = (rgbPixel >> 8) & 0xFF;
            // Standard luminance formula
            uint8_t gray = (uint8_t)(0.299 * r + 0.587 * g + 0.114 * b);
            m_imageData[(int)(y*actualWidth+x)] = gray;
        }
    }
    NSLog(@"[imageToGreyImage] width: %f, height: %f", actualWidth, actualHeight);
    free(rgbImage);
    return m_imageData;
}

+ (unsigned char *)format_K_threshold:(unsigned char *) orgpixels width:(NSInteger) xsize height:(NSInteger) ysize {
    unsigned char * despixels = malloc(xsize*ysize);
    int graytotal = 0;
    int k = 0;
    
    int i;
    int j;
    int gray;
    for(i = 0; i < ysize; ++i) {
        for(j = 0; j < xsize; ++j) {
            gray = orgpixels[k] & 255;
            graytotal += gray;
            ++k;
        }
    }
    
    int grayave = graytotal / ysize / xsize;
    int adjustedThreshold = grayave - (int)(grayave * 0.3); // 30% reduction for lighter image
    if (adjustedThreshold < 0) adjustedThreshold = 0;
    k = 0;
    for(i = 0; i < ysize; ++i) {
        for(j = 0; j < xsize; ++j) {
            gray = orgpixels[k] & 255;
            if(gray > adjustedThreshold) {
                despixels[k] = 0; // White pixel
            } else {
                despixels[k] = 1; // Black pixel
            }
            ++k;
        }
    }
    NSLog(@"[format_K_threshold] grayave: %d, adjustedThreshold: %d", grayave, adjustedThreshold);
    return despixels;
}

+ (NSData *)eachLinePixToCmd:(unsigned char *)src nWidth:(NSInteger) nWidth nHeight:(NSInteger) nHeight nMode:(NSInteger) nMode {
    int p0[] = { 0, 0x80 };
    int p1[] = { 0, 0x40 };
    int p2[] = { 0, 0x20 };
    int p3[] = { 0, 0x10 };
    int p4[] = { 0, 0x08 };
    int p5[] = { 0, 0x04 };
    int p6[] = { 0, 0x02 };
    
    NSInteger nBytesPerLine = (int)nWidth/8;
    unsigned char * data = malloc(nHeight*(8+nBytesPerLine));
    NSInteger k = 0;
    
    for(int i=0;i<nHeight;i++){
        NSInteger var10 = i*(8+nBytesPerLine);
        //GS v 0 m xL xH yL yH d1....dk 打印光栅位图
        data[var10 + 0] = 29;//GS
        data[var10 + 1] = 118;//v
        data[var10 + 2] = 48;//0
        data[var10 + 3] =  (unsigned char)(nMode & 1);
        data[var10 + 4] =  (unsigned char)(nBytesPerLine % 256);//xL
        data[var10 + 5] =  (unsigned char)(nBytesPerLine / 256);//xH
        data[var10 + 6] = 1;//yL
        data[var10 + 7] = 0;//yH
        
        for (int j = 0; j < nBytesPerLine; ++j) {
            data[var10 + 8 + j] = (int) (p0[src[k]] + p1[src[k + 1]] + p2[src[k + 2]] + p3[src[k + 3]] + p4[src[k + 4]] + p5[src[k + 5]] + p6[src[k + 6]] + src[k + 7]);
            k =k+8;
        }
    }
    return [NSData dataWithBytes:data length:nHeight*(8+nBytesPerLine)];
}

-(UIImage *)getPrintImage:(UIImage *)image
           printerOptions:(NSDictionary *)options {

   // Use the original image size, no scaling
   CGFloat paddingX = 0;
   CGFloat paddingY = 0;

   // Allow optional padding from JS
   NSNumber* nPaddingX = [options valueForKey:@"paddingX"];
   NSNumber* nPaddingY = [options valueForKey:@"paddingY"];
   if(nPaddingX != nil) {
       paddingX = [nPaddingX floatValue];
   }
   if(nPaddingY != nil) {
       paddingY = [nPaddingY floatValue];
   }

   // Only add padding if needed
   if (paddingX > 0 || paddingY > 0) {
       CGSize orgSize = [image size];
       CGSize size = CGSizeMake(orgSize.width + paddingX, orgSize.height + paddingY);
       UIGraphicsBeginImageContext(size);
       CGContextRef context = UIGraphicsGetCurrentContext();
       CGContextSetFillColorWithColor(context, [[UIColor whiteColor] CGColor]);
       CGContextFillRect(context, CGRectMake(0, 0, size.width, size.height));
       [image drawInRect:CGRectMake(paddingX, paddingY, orgSize.width, orgSize.height)
                blendMode:kCGBlendModeNormal alpha:1.0];
       UIImage *paddedImage = UIGraphicsGetImageFromCurrentImageContext();
       UIGraphicsEndImageContext();
       return paddedImage;
   } else {
       // No padding, return original image
       return image;
   }
}

-(UIImage *)addImagePadding:(UIImage * )image
                   paddingX: (CGFloat) paddingX
                   paddingY: (CGFloat) paddingY
{
    CGFloat width = image.size.width + paddingX;
    CGFloat height = image.size.height + paddingY;

    UIGraphicsBeginImageContextWithOptions(CGSizeMake(width, height), true, 0.0);
    CGContextRef context = UIGraphicsGetCurrentContext();
    CGContextSetFillColorWithColor(context, [UIColor whiteColor].CGColor);
    CGContextSetInterpolationQuality(context, kCGInterpolationHigh);
    CGContextFillRect(context, CGRectMake(0, 0, width, height));
    CGFloat originX = (width - image.size.width)/2;
    CGFloat originY = (height -  image.size.height)/2;
    CGImageRef immageRef = image.CGImage;
    CGContextDrawImage(context, CGRectMake(originX, originY, image.size.width, image.size.height), immageRef);
    CGImageRef newImageRef = CGBitmapContextCreateImage(context);
    UIImage* paddedImage = [UIImage imageWithCGImage:newImageRef];

    CGImageRelease(newImageRef);
    UIGraphicsEndImageContext();

    return paddedImage;
}

RCT_EXPORT_METHOD(closeConn) {
    @try {
        !connected_ip ? [NSException raise:@"Invalid connection" format:@"Can't connect to printer"] : nil;
        [[PrinterSDK defaultPrinterSDK] disconnect];
        connected_ip = nil;
    } @catch (NSException *exception) {
        NSLog(@"%@", exception.reason);
    }
}

@end